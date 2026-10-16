# Tempo take-home

Two parts, both in this repo:

- `src/main/kotlin/Take Home Assessment - Hierarchy.kt` — the Hierarchy `filter()` task, with a test suite.
- `src/main/kotlin/Take Home Assessment - SimpleCache.md` — code review of the `SimpleCache` implementation.

## Part 1 — Hierarchy.filter

### The data structure

A forest (one or more trees) stored flat as two parallel arrays: node IDs and depths, both in DFS
order. A node's parent is the nearest earlier node whose depth is exactly one less. The first
element is always depth 0, and depth can only go up by one (a child), stay put (a sibling), or drop
(moving back up). That invariant is what keeps the whole thing cheap.

### How filter works

Rule from the problem: keep a node only if its own ID passes the predicate and every ancestor does
too. In practice that means if a parent is dropped, its whole subtree is dropped, even when a
grandchild would have passed on its own.

I do a single pass with a stack that holds the current ancestor path, each entry being (depth,
survived). For every node:

1. Pop anything with depth >= this node's depth, so the top is its immediate parent, or the stack
   is empty for a root.
2. Keep the node iff the parent survived (or it's a root) and the predicate says yes.
3. Push the node onto the stack either way.

That last step is the subtle one. If you only push nodes that survived, a removed parent disappears
from the stack, its children stop seeing it as a failed ancestor, and you leak nodes back into the
output. Pushing every node regardless is what makes a dropped parent correctly take its whole
subtree with it.

O(n) time, O(n) space, one pass, no scanning backwards to find parents.

### Assumptions

- The depths array follows the stated invariants; I didn't add validation.
- Node IDs are unique, as the problem says.
- filter returns a new Hierarchy and leaves the input alone.
- A root that fails the predicate removes its whole tree; no descendant survives a failed root.

### Tests

I covered empty input, a single node, all pass and all fail, a failed root killing its subtree, a
failed parent killing descendants that would otherwise pass, an independent sibling branch surviving
while another branch is removed, multiple root trees, and a deep chain. I traced each expected
string by hand rather than trusting the output.

### Running it

Open the project in IntelliJ as a Kotlin/JVM project (the tests use TestNG, so make sure TestNG is
on the module's classpath), then run the `FilterTest` class.

## Part 2 — SimpleCache review

`Take Home Assessment - SimpleCache.md` covers the memory leak from never-evicted entries, the cache
stampede from unglued get/put, the wall-clock TTL problem, the underspecified TTL semantics, the
misleading `size()`, the missing metrics, and what's actually fine about the concurrency.
