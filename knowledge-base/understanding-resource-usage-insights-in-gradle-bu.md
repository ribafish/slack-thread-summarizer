# Understanding Resource Usage Insights in Gradle Build Scans

**Keywords:** Gradle, Build Scans, Resource Usage Insights, Memory Usage, Docker, JVM, Performance

## Overview

This article explains how memory usage is reported in Gradle Build Scans, specifically within the Resource Usage Insights feature. It addresses common questions related to memory reporting discrepancies and clarifies how the reported metrics relate to Docker container limits, JVM memory usage, and overall system memory.

## Memory Reporting in Resource Usage Insights

Resource Usage Insights in Gradle Build Scans provide data about the resources consumed during a build. However, the interpretation of these numbers requires understanding how they are measured and what they represent.

### Total Memory vs. Docker Container Memory

When running builds within Docker containers, the total memory reported by Resource Usage Insights represents the total physical memory of the host machine, **not** the memory allocated specifically to the Docker container. Therefore, the reported "total memory" may exceed the container's memory limit.

### Reserved Memory vs. JVM Heap Memory

The memory reported by Resource Usage Insights reflects the *reserved* memory allocated to a process, which is different from the JVM heap memory displayed on the Performance Overview page of the build scan. The JVM uses additional memory beyond the heap, including:

*   Metaspace
*   Other non-heap memory regions

Therefore, the total memory used by the JVM is typically larger than the heap memory.

### Impact of OS Memory Management

In certain situations, the heap memory usage of the JVM might exceed the reserved memory reported in Resource Usage Insights. This can occur if the operating system employs techniques such as:

*   Memory compression
*   Swapping memory out of physical RAM

These OS-level optimizations can allow a process to utilize more memory than initially reserved, though performance may be impacted.

## Memory Usage for All Processes

The "All processes" metric in Resource Usage Insights includes memory allocated to processes both inside and outside the Docker container. As a result, the "All processes" memory usage can exceed the Docker container's memory limit.

Refer to [this GitHub issue](https://github.com/gradle/dv/issues/36940#issuecomment-2217316731) for related information.


---

**Source:** [Slack Thread](https://kaminoalumni.slack.com/archives/C09LP0ZSUL9/p1760396863764819)