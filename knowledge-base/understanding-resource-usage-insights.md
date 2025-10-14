# Understanding Resource Usage Insights

**Keywords:** resource usage, memory allocation, docker containers, JVM, build performance, profiling

## Overview

This article explains how resource usage is measured and reported within the build scan, particularly concerning memory allocation in Docker containers and JVM processes. It addresses discrepancies that may arise between the reported values and the actual memory available or used.

## Memory Measurement in Docker Containers

When running builds inside Docker containers, the "total memory" reported by resource usage insights represents the total physical memory of the host machine, *not* the memory limit imposed on the Docker container. This means the reported value may exceed the memory allocated to the container itself.

## Memory Allocation for JVM Processes

Resource usage insights captures the *reserved* memory allocated to a process, rather than the JVM heap memory reported on the performance overview page. The JVM utilizes memory beyond the heap, including metaspace and other non-heap regions. Consequently, the memory usage displayed via resource usage insights is expected to be larger than the heap size reported.

Moreover, the operating system may compress or swap JVM heap memory, resulting in situations where the actual heap memory used by the JVM exceeds the reserved memory reported by resource usage insights.

## Scope of Resource Monitoring ("All Processes")

The "All processes" metric in resource usage insights includes memory and CPU usage from *all* processes running on the host machine, including those outside the Docker container.  Therefore, the value observed for "All processes" may exceed the memory limit set for the Docker container.

See: <https://github.com/gradle/dv/issues/36940#issuecomment-2217316731>


---

**Source:** [Slack Thread](https://kaminoalumni.slack.com/archives/C09LP0ZSUL9/p1760396863764819)