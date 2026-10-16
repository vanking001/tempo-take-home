import org.testng.Assert.assertEquals
import org.testng.annotations.Test

// The task:
// 1. Read and understand the Hierarchy data structure described in this file.
// 2. Implement filter() function.
// 3. Implement more test cases.
//
// The task should take 30-90 minutes.
//
// When assessing the submission, we will pay attention to:
// - correctness, efficiency, and clarity of the code;
// - the test cases.

/**
 * A `Hierarchy` stores an arbitrary _forest_ (an ordered collection of ordered trees)
 * as an array of node IDs in the order of DFS traversal, combined with a parallel array of node depths.
 *
 * Parent-child relationships are identified by the position in the array and the associated depth.
 * Each tree root has depth 0, its children have depth 1 and follow it in the array, their children have depth 2 and follow them, etc.
 *
 * Example:
 * ```
 * nodeIds: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11
 * depths:  0, 1, 2, 3, 1, 0, 1, 0, 1, 1, 2
 * ```
 *
 * the forest can be visualized as follows:
 * ```
 * 1
 * - 2
 * - - 3
 * - - - 4
 * - 5
 * 6
 * - 7
 * 8
 * - 9
 * - 10
 * - - 11
 *```
 * 1 is a parent of 2 and 5, 2 is a parent of 3, etc. Note that depth is equal to the number of hyphens for each node.
 *
 * Invariants on the depths array:
 *  * Depth of the first element is 0.
 *  * If the depth of a node is `D`, the depth of the next node in the array can be:
 *      * `D + 1` if the next node is a child of this node;
 *      * `D` if the next node is a sibling of this node;
 *      * `d < D` - in this case the next node is not related to this node.
 */
interface Hierarchy {
  /** The number of nodes in the hierarchy. */
  val size: Int

  /**
   * Returns the unique ID of the node identified by the hierarchy index. The depth for this node will be `depth(index)`.
   * @param index must be non-negative and less than [size]
   * */
  fun nodeId(index: Int): Int

  /**
   * Returns the depth of the node identified by the hierarchy index. The unique ID for this node will be `nodeId(index)`.
   * @param index must be non-negative and less than [size]
   * */
  fun depth(index: Int): Int

  fun formatString(): String {
    return (0 until size).joinToString(
      separator = ", ",
      prefix = "[",
      postfix = "]"
    ) { i -> "${nodeId(i)}:${depth(i)}" }
  }
}

/**
 * A node is present in the filtered hierarchy iff its node ID passes the predicate and all of its ancestors pass it as well.
 */
fun Hierarchy.filter(nodeIdPredicate: (Int) -> Boolean): Hierarchy {
  val resultNodeIds = ArrayList<Int>()
  val resultDepths = ArrayList<Int>()

  // Stack of (depth, kept) for the ancestor path of the current node.
  val stackDepth = IntArray(size)
  val stackKept = BooleanArray(size)
  var stackSize = 0

  for (i in 0 until size) {
    val id = nodeId(i)
    val d = depth(i)

    // Pop ancestors that are no longer on this node's path.
    while (stackSize > 0 && stackDepth[stackSize - 1] >= d) {
      stackSize--
    }

    // Roots (empty stack) have no parent, so they are not filtered by ancestry.
    val parentKept = stackSize == 0 || stackKept[stackSize - 1]
    val kept = parentKept && nodeIdPredicate(id)

    // Record this node on the path (even if removed, descendants still need to
    // know it failed so that they too are dropped).
    stackDepth[stackSize] = d
    stackKept[stackSize] = kept
    stackSize++

    if (kept) {
      resultNodeIds.add(id)
      resultDepths.add(d)
    }
  }

  return ArrayBasedHierarchy(
          resultNodeIds.toIntArray(),
          resultDepths.toIntArray(),
  )
}

class ArrayBasedHierarchy(
  private val myNodeIds: IntArray,
  private val myDepths: IntArray,
) : Hierarchy {
  override val size: Int = myDepths.size

  override fun nodeId(index: Int): Int = myNodeIds[index]

  override fun depth(index: Int): Int = myDepths[index]
}

class FilterTest {
  private val exampleNodes = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
  private val exampleDepths = intArrayOf(0, 1, 2, 3, 1, 0, 1, 0, 1, 1, 2)

  @Test
  fun testFilter() {
    val unfiltered: Hierarchy = ArrayBasedHierarchy(exampleNodes, exampleDepths)
    val filteredActual: Hierarchy = unfiltered.filter { nodeId -> nodeId % 3 != 0 }
    val filteredExpected: Hierarchy = ArrayBasedHierarchy(
      intArrayOf(1, 2, 5, 8, 10, 11),
      intArrayOf(0, 1, 1, 0, 1, 2))
    assertEquals(filteredActual.formatString(), filteredExpected.formatString())
  }

  @Test
  fun testFilterEmptyHierarchy() {
    val unfiltered: Hierarchy = ArrayBasedHierarchy(intArrayOf(), intArrayOf())
    assertEquals(unfiltered.filter { true }.formatString(), "[]")
    assertEquals(unfiltered.filter { false }.formatString(), "[]")
  }

  @Test
  fun testFilterAllPassIsIdentity() {
    val unfiltered: Hierarchy = ArrayBasedHierarchy(exampleNodes, exampleDepths)
    assertEquals(unfiltered.filter { true }.formatString(), unfiltered.formatString())
  }

  @Test
  fun testFilterAllFailIsEmpty() {
    val unfiltered: Hierarchy = ArrayBasedHierarchy(exampleNodes, exampleDepths)
    assertEquals(unfiltered.filter { false }.formatString(), "[]")
  }

  @Test
  fun testSingleNode() {
    assertEquals(ArrayBasedHierarchy(intArrayOf(1), intArrayOf(0)).filter { true }.formatString(), "[1:0]")
    assertEquals(ArrayBasedHierarchy(intArrayOf(1), intArrayOf(0)).filter { false }.formatString(), "[]")
  }

  @Test
  fun testRootFailsRemovesWholeSubtree() {
    // Root 1 fails the predicate, children 2 and 3 pass -> the entire tree is dropped.
    val unfiltered = ArrayBasedHierarchy(intArrayOf(1, 2, 3), intArrayOf(0, 1, 2))
    assertEquals(unfiltered.filter { it != 1 }.formatString(), "[]")
  }

  @Test
  fun testParentFailsRemovesDescendants() {
    // Root 1 passes, child 2 fails, grandchildren 3 and 4 pass -> 3 and 4 are still dropped.
    val unfiltered = ArrayBasedHierarchy(intArrayOf(1, 2, 3, 4), intArrayOf(0, 1, 2, 3))
    assertEquals(unfiltered.filter { it != 2 }.formatString(), "[1:0]")
  }

  @Test
  fun testRemovedSiblingDoesNotAffectOtherSiblingSubtree() {
    // 1(0) - 2(1), 3(1) - 4(2). Node 2 is removed, node 3 survives with its child 4.
    val unfiltered = ArrayBasedHierarchy(intArrayOf(1, 2, 3, 4), intArrayOf(0, 1, 1, 2))
    assertEquals(unfiltered.filter { it != 2 }.formatString(), "[1:0, 3:1, 4:2]")
  }

  @Test
  fun testMultipleRootTrees() {
    // Tree A: 1(0)-2(1). Tree B: 3(0)-4(1)-5(2), 6(1)-7(2).
    // Keep ids < 4 -> 1, 2, 3 survive; 4, 5, 6, 7 are dropped.
    val unfiltered = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3, 4, 5, 6, 7),
      intArrayOf(0, 1, 0, 1, 2, 1, 2))
    assertEquals(unfiltered.filter { it < 4 }.formatString(), "[1:0, 2:1, 3:0]")
  }

  @Test
  fun testDeepNestingKeepsChain() {
    // A deep chain 1-2-3-4-5 all pass.
    val unfiltered = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3, 4, 5),
      intArrayOf(0, 1, 2, 3, 4))
    assertEquals(unfiltered.filter { true }.formatString(), "[1:0, 2:1, 3:2, 4:3, 5:4]")
  }

  @Test
  fun testResultKeepsOriginalIdsAndDepths() {
    // Predicate selects only some ids; depths of surviving nodes must be unchanged.
    val unfiltered = ArrayBasedHierarchy(exampleNodes, exampleDepths)
    val filtered = unfiltered.filter { it % 2 == 0 } // 2, 4, 6, 8, 10 pass
    // 2 has parent 1 (odd, removed) -> dropped. 4's parent 3 removed -> dropped.
    // 6 is a root and passes -> kept. 8 root passes -> kept; 9 (child of 8, odd) removed; 10 (child of 8) kept.
    assertEquals(filtered.formatString(), "[6:0, 8:0, 10:1]")
  }
}