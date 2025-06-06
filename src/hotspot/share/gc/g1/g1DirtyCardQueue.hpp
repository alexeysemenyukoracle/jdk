/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#ifndef SHARE_GC_G1_G1DIRTYCARDQUEUE_HPP
#define SHARE_GC_G1_G1DIRTYCARDQUEUE_HPP

#include "gc/g1/g1CardTable.hpp"
#include "gc/g1/g1ConcurrentRefineStats.hpp"
#include "gc/g1/g1FreeIdSet.hpp"
#include "gc/shared/bufferNode.hpp"
#include "gc/shared/bufferNodeList.hpp"
#include "gc/shared/ptrQueue.hpp"
#include "memory/allocation.hpp"
#include "memory/padded.hpp"
#include "utilities/nonblockingQueue.hpp"

class G1PrimaryConcurrentRefineThread;
class G1DirtyCardQueueSet;
class G1RedirtyCardsQueueSet;
class Thread;

// A ptrQueue whose elements are "oops", pointers to object heads.
class G1DirtyCardQueue: public PtrQueue {
  G1ConcurrentRefineStats* _refinement_stats;

public:
  G1DirtyCardQueue(G1DirtyCardQueueSet* qset);

  // Flush before destroying; queue may be used to capture pending work while
  // doing something else, with auto-flush on completion.
  ~G1DirtyCardQueue();

  G1ConcurrentRefineStats* refinement_stats() const {
    return _refinement_stats;
  }

  // Compiler support.
  static ByteSize byte_offset_of_index() {
    return PtrQueue::byte_offset_of_index<G1DirtyCardQueue>();
  }
  using PtrQueue::byte_width_of_index;

  static ByteSize byte_offset_of_buf() {
    return PtrQueue::byte_offset_of_buf<G1DirtyCardQueue>();
  }
  using PtrQueue::byte_width_of_buf;

};

class G1DirtyCardQueueSet: public PtrQueueSet {
  // Head and tail of a list of BufferNodes, linked through their next()
  // fields.  Similar to BufferNodeList, but without the _entry_count.
  struct HeadTail {
    BufferNode* _head;
    BufferNode* _tail;
    HeadTail() : _head(nullptr), _tail(nullptr) {}
    HeadTail(BufferNode* head, BufferNode* tail) : _head(head), _tail(tail) {}
  };

  // Concurrent refinement may stop processing in the middle of a buffer if
  // there is a pending safepoint, to avoid long delays to safepoint.  A
  // partially processed buffer needs to be recorded for processing by the
  // safepoint if it's a GC safepoint; otherwise it needs to be recorded for
  // further concurrent refinement work after the safepoint.  But if the
  // buffer was obtained from the completed buffer queue then it can't simply
  // be added back to the queue, as that would introduce a new source of ABA
  // for the queue.
  //
  // The PausedBuffer object is used to record such buffers for the upcoming
  // safepoint, and provides access to the buffers recorded for previous
  // safepoints.  Before obtaining a buffer from the completed buffers queue,
  // we first transfer any buffers from previous safepoints to the queue.
  // This is ABA-safe because threads cannot be in the midst of a queue pop
  // across a safepoint.
  //
  // The paused buffers are conceptually an extension of the completed buffers
  // queue, and operations which need to deal with all of the queued buffers
  // (such as concatenating or abandoning logs) also need to deal with any
  // paused buffers.  In general, if a safepoint performs a GC then the paused
  // buffers will be processed as part of it, and there won't be any paused
  // buffers after a GC safepoint.
  class PausedBuffers {
    class PausedList : public CHeapObj<mtGC> {
      BufferNode* volatile _head;
      BufferNode* _tail;
      size_t _safepoint_id;

      NONCOPYABLE(PausedList);

    public:
      PausedList();
      DEBUG_ONLY(~PausedList();)

      // Return true if this list was created to hold buffers for the
      // next safepoint.
      // precondition: not at safepoint.
      bool is_next() const;

      // Thread-safe add the buffer to the list.
      // precondition: not at safepoint.
      // precondition: is_next().
      void add(BufferNode* node);

      // Take all the buffers from the list.  Not thread-safe.
      HeadTail take();
    };

    // The most recently created list, which might be for either the next or
    // a previous safepoint, or might be null if the next list hasn't been
    // created yet.  We only need one list because of the requirement that
    // threads calling add() must first ensure there are no paused buffers
    // from a previous safepoint.  There might be many list instances existing
    // at the same time though; there can be many threads competing to create
    // and install the next list, and meanwhile there can be a thread dealing
    // with the previous list.
    PausedList* volatile _plist;
    DEFINE_PAD_MINUS_SIZE(1, DEFAULT_PADDING_SIZE, sizeof(PausedList*));

    NONCOPYABLE(PausedBuffers);

  public:
    PausedBuffers();
    DEBUG_ONLY(~PausedBuffers();)

    // Thread-safe add the buffer to paused list for next safepoint.
    // precondition: not at safepoint.
    // precondition: does not have paused buffers from a previous safepoint.
    void add(BufferNode* node);

    // Thread-safe take all paused buffers for previous safepoints.
    // precondition: not at safepoint.
    HeadTail take_previous();

    // Take all the paused buffers.
    // precondition: at safepoint.
    HeadTail take_all();
  };

  DEFINE_PAD_MINUS_SIZE(0, DEFAULT_PADDING_SIZE, 0);
  // Upper bound on the number of cards in the completed and paused buffers.
  volatile size_t _num_cards;
  DEFINE_PAD_MINUS_SIZE(1, DEFAULT_PADDING_SIZE, sizeof(size_t));
  // If the queue contains more cards than configured here, the
  // mutator must start doing some of the concurrent refinement work.
  volatile size_t _mutator_refinement_threshold;
  DEFINE_PAD_MINUS_SIZE(2, DEFAULT_PADDING_SIZE, sizeof(size_t));
  // Buffers ready for refinement.
  // NonblockingQueue has inner padding of one cache line.
  NonblockingQueue<BufferNode, &BufferNode::next_ptr> _completed;
  // Add a trailer padding after NonblockingQueue.
  DEFINE_PAD_MINUS_SIZE(3, DEFAULT_PADDING_SIZE, sizeof(BufferNode*));
  // Buffers for which refinement is temporarily paused.
  // PausedBuffers has inner padding, including trailer.
  PausedBuffers _paused;

  G1FreeIdSet _free_ids;

  G1ConcurrentRefineStats _concatenated_refinement_stats;
  G1ConcurrentRefineStats _detached_refinement_stats;

  // Verify _num_cards == sum of cards in the completed queue.
  void verify_num_cards() const NOT_DEBUG_RETURN;

  // Thread-safe add a buffer to paused list for next safepoint.
  // precondition: not at safepoint.
  void record_paused_buffer(BufferNode* node);
  void enqueue_paused_buffers_aux(const HeadTail& paused);
  // Thread-safe transfer paused buffers for previous safepoints to the queue.
  // precondition: not at safepoint.
  void enqueue_previous_paused_buffers();
  // Transfer all paused buffers to the queue.
  // precondition: at safepoint.
  void enqueue_all_paused_buffers();

  void abandon_completed_buffers();

  // Refine the cards in "node" from its index to buffer_capacity.
  // Stops processing if SuspendibleThreadSet::should_yield() is true.
  // Returns true if the entire buffer was processed, false if there
  // is a pending yield request.  The node's index is updated to exclude
  // the processed elements, e.g. up to the element before processing
  // stopped, or one past the last element if the entire buffer was
  // processed. Updates stats.
  bool refine_buffer(BufferNode* node,
                     uint worker_id,
                     G1ConcurrentRefineStats* stats);

  // Deal with buffer after a call to refine_buffer.  If fully processed,
  // deallocate the buffer.  Otherwise, record it as paused.
  void handle_refined_buffer(BufferNode* node, bool fully_processed);

  // Thread-safe attempt to remove and return the first buffer from
  // the _completed queue.
  // Returns null if the queue is empty, or if a concurrent push/append
  // interferes. It uses GlobalCounter critical section to avoid ABA problem.
  BufferNode* dequeue_completed_buffer();
  // Remove and return a completed buffer from the list, or return null
  // if none available.
  BufferNode* get_completed_buffer();

  // Called when queue is full or has no buffer.
  void handle_zero_index(G1DirtyCardQueue& queue);

  // Enqueue the buffer, and optionally perform refinement by the mutator.
  // Mutator refinement is only done by Java threads, and only if there
  // are more than mutator_refinement_threshold cards in the completed buffers.
  // Updates stats.
  //
  // Mutator refinement, if performed, stops processing a buffer if
  // SuspendibleThreadSet::should_yield(), recording the incompletely
  // processed buffer for later processing of the remainder.
  void handle_completed_buffer(BufferNode* node, G1ConcurrentRefineStats* stats);

public:
  G1DirtyCardQueueSet(BufferNode::Allocator* allocator);
  ~G1DirtyCardQueueSet();

  // The number of parallel ids that can be claimed to allow collector or
  // mutator threads to do card-processing work.
  static uint num_par_ids();

  static void handle_zero_index_for_thread(Thread* t);

  virtual void enqueue_completed_buffer(BufferNode* node);

  // Upper bound on the number of cards currently in this queue set.
  // Read without synchronization.  The value may be high because there
  // is a concurrent modification of the set of buffers.
  size_t num_cards() const;

  void merge_bufferlists(G1RedirtyCardsQueueSet* src);

  BufferNodeList take_all_completed_buffers();

  void flush_queue(G1DirtyCardQueue& queue);

  using CardValue = G1CardTable::CardValue;
  void enqueue(G1DirtyCardQueue& queue, volatile CardValue* card_ptr);

  // If there are more than stop_at cards in the completed buffers, pop
  // a buffer, refine its contents, and return true.  Otherwise return
  // false.  Updates stats.
  //
  // Stops processing a buffer if SuspendibleThreadSet::should_yield(),
  // recording the incompletely processed buffer for later processing of
  // the remainder.
  bool refine_completed_buffer_concurrently(uint worker_id,
                                            size_t stop_at,
                                            G1ConcurrentRefineStats* stats);

  // If a full collection is happening, reset per-thread refinement stats and
  // partial logs, and release completed logs. The full collection will make
  // them all irrelevant.
  // precondition: at safepoint.
  void abandon_logs_and_stats();

  // Update global refinement statistics with the ones given and the ones from
  // detached threads.
  // precondition: at safepoint.
  void update_refinement_stats(G1ConcurrentRefineStats& stats);
  // Add the given thread's partial logs to the global list and return and reset
  // its refinement stats.
  // precondition: at safepoint.
  G1ConcurrentRefineStats concatenate_log_and_stats(Thread* thread);

  // Return the total of mutator refinement stats for all threads.
  // precondition: at safepoint.
  // precondition: only call after concatenate_logs_and_stats.
  G1ConcurrentRefineStats concatenated_refinement_stats() const;

  // Accumulate refinement stats from threads that are detaching.
  void record_detached_refinement_stats(G1ConcurrentRefineStats* stats);

  // Number of cards above which mutator threads should do refinement.
  size_t mutator_refinement_threshold() const;

  // Set number of cards above which mutator threads should do refinement.
  void set_mutator_refinement_threshold(size_t value);
};

#endif // SHARE_GC_G1_G1DIRTYCARDQUEUE_HPP
