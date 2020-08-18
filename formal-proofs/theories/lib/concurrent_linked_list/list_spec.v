From SegmentQueue.lib.concurrent_linked_list Require Export segment_spec.
From iris.program_logic Require Import atomic.
From iris.heap_lang Require Export proofmode notation lang.
Open Scope nat.

Record listSpec Σ `{!heapG Σ} {impl: segmentInterface}
       (segment_spec: segmentSpec Σ impl) :=
  ListSpec {
      list_name: Type;
      is_concurrentLinkedList: namespace -> list_name -> iProp Σ;
      is_concurrentLinkedList_persistent N γ:
        Persistent (is_concurrentLinkedList N γ);
      segment_in_list
        (γ: list_name)
        (γs: linkedListNode_name _ _ (linkedListNode_base _ _ segment_spec))
        (id: nat)
        (v: val): iProp Σ;
      segment_in_list_persistent γ γs id v:
        Persistent (segment_in_list γ γs id v);
      segment_is_cancelled: list_name -> nat -> iProp Σ;
      segment_is_cancelled_persistent γ id:
        Persistent (segment_is_cancelled γ id);
      pointer_location: list_name -> loc -> nat -> iProp Σ;
      newList: val;
      cleanPrev: val;
      findSegment: val;
      moveForward: val;
      onSlotCleaned: val;
      newList_spec N (k: nat):
        {{{ ⌜k > 0⌝ ∧ initialization_requirements _ _ segment_spec }}}
          newList #k
        {{{ γ ℓ, RET #ℓ; is_concurrentLinkedList N γ ∗
                        [∗ list] i ∈ seq 0 k,
                            pointer_location γ (ℓ +ₗ (i: nat)) 0
        }}};
      findSegment_spec N γ γs' (start_id id: nat) v:
        {{{ is_concurrentLinkedList N γ ∗ segment_in_list γ γs' start_id v }}}
          findSegment v #id
        {{{ (v': val) (id': nat), RET (SOMEV v');
          (∃ γs, segment_in_list γ γs id' v')
          ∗ ⌜(start_id ≤ id' ∧ id ≤ id')%nat⌝
          ∗ ∀ i, (⌜max start_id id ≤ i < id'⌝)%nat -∗ segment_is_cancelled γ i
        }}};
      moveForward_spec N γ γs (ℓ: loc) id v:
        is_concurrentLinkedList N γ -∗
        segment_in_list γ γs id v -∗
        <<< ∀ id', ▷ pointer_location γ ℓ id' >>>
          moveForward #ℓ v @ ⊤ ∖ ↑N
        <<< ∃ (r: bool), if r then pointer_location γ ℓ (max id id')
                              else ▷ pointer_location γ ℓ id'
                                   ∗ segment_is_cancelled γ id,
            RET #r >>>;
      cleanPrev_spec N γ γs id ptr:
        {{{ is_concurrentLinkedList N γ ∗ segment_in_list γ γs id ptr }}}
          cleanPrev ptr
        {{{ RET #(); True }}};
      onSlotCleaned_spec N Ψ γ γs id p:
        is_concurrentLinkedList N γ -∗
        segment_in_list γ γs id p -∗
        <<< (∀ n, segment_content _ _ segment_spec γs n ==∗
            Ψ ∗ ∃ n', ⌜(n = S n')%nat⌝ ∧
                      segment_content _ _ segment_spec γs n') >>>
          onSlotCleaned p @ ⊤ ∖ ↑N
        <<< Ψ, RET #() >>>;
      pointer_location_load γ (ℓ: loc):
        ⊢ <<< ∀ id, ▷ pointer_location γ ℓ id >>>
          ! #ℓ @ ⊤
        <<< ∃ γs p, pointer_location γ ℓ id ∗ segment_in_list γ γs id p,
            RET p >>>;
      access_segment N (known_is_removed: bool) E γ γs id ptr:
        ↑N ⊆ E ->
        is_concurrentLinkedList N γ -∗
        segment_in_list γ γs id ptr -∗
        (if known_is_removed then segment_is_cancelled γ id else True) -∗
        |={E, E ∖ ↑N}=> ∃ n, ⌜known_is_removed = false ∨ n = 0⌝ ∧
                            ▷ segment_content _ _ _ γs n ∗
                            (▷ segment_content _ _ _ γs n ={E ∖ ↑N, E}=∗ emp);
    }.
