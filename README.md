# pharrellel-test

![pharrel](http://cl.ly/image/0W3K0Z2n3n2d/Image%202014-03-25%20at%201.00.52%20PM.png)

A parallel test runner for clojure.test

## Installation

Install via clojars: https://clojars.org/pharrellel-test

## Warnings

Whilst pharrallel will run fixtures, it does *not* isolate them from each other, or indeed do anything sane at all. I don't use fixtures, and consider the global mutable state they imply a smell. If you use fixtures, generally:

_do not use this library_

I'll accept a PR for this, and gladly point somebody else in the right direction (you want to group tests by whether they are in namespaces with fixtures or not, run the ones with fixtures in a single thread, and have the other workers run the tests that don't require serialization.

# Usage:

Not using fixtures? Great! Here's how to use pharallel:

There's one function you care about:

(run-tests ns-regex)

It takes a regex for matching which namespaces you want to run tests in. It returns the same test summary you'd get from `clojure.test/run-tests`.

Optionally, it takes a second argument, which is the number of worker threads to use. This defaults to the number of CPUs, as returned by JVM CALL HERE

# How do it do it?

Most of pharrallel is modified from clojure.test. There's two phases: gather, which scans namespaces for tests and puts them on a queue, and running, which spawns a future for each thread, and polls from the gather queue, running tests, and dropping results on an output queue. Once the workers are done (which is indicated by the queue being empty, and signaled to the runner via a promise per worker), a doseq prints results

## License

Copyright © 2014 Tom Crayford

Distributed under the Eclipse Public License either version 1.0
