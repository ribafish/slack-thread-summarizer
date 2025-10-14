# Understanding Resource Usage Insights: Memory Reporting

**Keywords:** resource usage insights, memory, JVM, Docker, CircleCI, build performance, process memory

## Overview

This article explains how memory usage is reported in Resource Usage Insights, addressing discrepancies between reported memory values and expected container limits, JVM heap sizes, and overall system memory. It clarifies how memory is measured within Docker containers and the relationship between reserved memory, heap memory, and total system memory.

## Memory Reporting in Docker Containers

When running processes within Docker containers, Resource Usage Insights reports memory based on the following principles:

*   **Total Memory vs. Container Memory:** The total memory reported by Resource Usage Insights is the physical memory installed on the host machine running the Docker container, *not* the memory limit configured for the Docker container itself. Therefore, the reported total memory might be higher than the expected memory limit set for the container.

*   **`All processes` Memory:** The memory attributed to "All processes" includes memory allocated to processes both inside and outside the Docker container. This can result in the `All processes` value exceeding the memory limit configured for the container.

## JVM Memory Usage

Resource Usage Insights captures memory usage related to the Java Virtual Machine (JVM) with these considerations:

*   **Reserved vs. Heap Memory:** The memory reported by Resource Usage Insights represents the *reserved memory* allocated for the process, *not* the JVM's heap memory.

*   **JVM Memory Components:** JVM memory usage extends beyond the heap. The JVM utilizes metaspace and other non-heap memory regions. As a result, the overall memory used by the JVM is typically larger than the heap memory size configured for the application.

*   **Operating System Effects:** The actual heap memory used by the JVM can sometimes exceed the reserved memory reported by Resource Usage Insights. This can occur if the operating system compresses or swaps memory out of physical memory.

## External Resources

*   [Gradle DV Issue 36940](https://github.com/gradle/dv/issues/36940#issuecomment-2217316731): Discussion related to memory and CPU usage reporting outside of the container context.


---

**Source:** [Slack Thread](https://app.slack.com/client/C09LP0ZSUL9/thread/C09LP0ZSUL9/1760396863764819)