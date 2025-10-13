# Understanding Resource Usage Insights in Build Scans

**Keywords:** build scans, resource usage, memory allocation, JVM, Docker, CircleCI, performance monitoring

## Overview

This article explains how resource usage insights are measured and reported in build scans, specifically addressing memory usage discrepancies observed in Docker containers and differences between reserved memory, heap memory, and total system memory.

## Memory Measurement in Docker Containers

When running build processes inside Docker containers, the "total memory" reported by resource usage insights represents the total physical memory of the host machine, not the memory limit imposed on the Docker container. For example, if a CircleCI runner has 40GB allocated to Docker but the host machine has 68GB, the resource usage insights will report 68GB as the total memory.

## Reserved Memory vs. JVM Heap Memory

The memory captured by resource usage insights reflects the *reserved* memory allocated for the process, not the JVM heap memory reported on the performance overview page. The JVM uses memory beyond the heap, including metaspace and other non-heap regions. Consequently, the overall JVM memory footprint typically exceeds the reported heap memory. Furthermore, the JVM heap memory used might even exceed the reserved memory reported if the operating system performs memory compression or swapping.

## Memory Usage of All Processes

The "All processes" metric in resource usage insights encompasses memory allocated to all processes on the host machine, including those running outside the Docker container.

See: [Gradle DV Issue 36940](https://github.com/gradle/dv/issues/36940#issuecomment-2217316731) for more details.


---

**Source:** [Slack Thread](https://app.slack.com/client/C09LP0ZSUL9/thread/C09LP0ZSUL9/1760396863764819)