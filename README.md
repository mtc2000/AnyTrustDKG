[![DOI](https://zenodo.org/badge/828109644.svg)](https://zenodo.org/doi/10.5281/zenodo.12736462)

> *Scalable and Adaptively Secure Any-Trust Distributed Key Generation and All-hands Checkpointing* Accepted by [CCS'24](https://www.sigsac.org/ccs/CCS2024).

## Introduction

This document is the instruction for the [CCS'24](https://www.sigsac.org/ccs/CCS2024) artifact evaluation for paper *Scalable and Adaptively Secure Any-Trust Distributed Key Generation and All-hands Checkpointing*.
The paper delivers a new Distributed Key Generation (DKG) protocol, namely Any-Trust DKG. In short, Any-Trust DKG delegates the most costly operations to an Any-Trust group together with a set of techniques for adaptive security. The Any-Trust group is randomly sampled and consists of a small number of individuals. The population only trusts that at least one member in the group is honest, without knowing which one.

This document belongs to the artifact, which is available online at [Zenodo](https://zenodo.org/doi/10.5281/zenodo.12736462) and [GitHub](https://github.com/mtc2000/AnyTrustDKG-ArtifactEvaluation). The accepted version of the CCS paper can also be found within this repository ([ccs2024b-paper583.accepted-version.pdf](ccs2024b-paper583.accepted-version.pdf)).

This artifact contains the proof-of-concept implementation and experiment results of Any-Trust DKG proposed in the paper.

Core components are as follows:
- [Verifiable random function](atdkg/src/main/java/org/ccs24/atdkg/VRF.java)
- [Multi-recipient encryption](atdkg/src/main/java/org/ccs24/atdkg/VEnc.java)
- [Point-to-point synchronized communication library](atdkg/src/main/java/org/ccs24/atdkg/DataPacketTimeoutBuffer.java) allowing timeout, based on [mpc4j-v1.1.1](https://github.com/alibaba-edu/mpc4j/releases/tag/v1.1.1)

## Obtain the Artifact

This artifact, a copy of this repository, can be accessed via the following links: [Zenodo](https://zenodo.org/doi/10.5281/zenodo.12736462) and [GitHub](https://github.com/mtc2000/AnyTrustDKG-ArtifactEvaluation).

## Artifact Overview

### Software Dependencies

- Java 17. Recommend installing use [sdkman](https://sdkman.io/install).
- Maven 3. Recommend installing use [sdkman](https://sdkman.io/install).
- [GNU data mash](https://www.gnu.org/software/datamash/download/), allowing easy arithmetic on experiment log statistics.
- Key library used: [mpc4j-v1.1.1](https://github.com/alibaba-edu/mpc4j/releases/tag/v1.1.1)

This implementation has been successfully tested on

- [Amazon Linux 2023 AMI **2023.4.20240416.0** x86_64](https://docs.aws.amazon.com/linux/al2023/release-notes/relnotes-2023.4.20240416.html) with kernel version `6.1.84-99.169.amzn2023`.
- Ubuntu **22.04.4 LTS** (x86_64) with kernel version `5.15.0-113-generic`.
- [GitHub Actions Runner Images Ubuntu **22.04.4 LTS** 20240708.1.0](https://github.com/actions/runner-images/blob/1b535372a075188bfce11fbd27d72a254c736b3c/images/ubuntu/Ubuntu2204-Readme.md) with kernel version `6.5.0-1023-azure`.

This artifact does not use or require GPU/CUDA.

### Code Structure
The following is a brief introduction to the directory structure of this artifact:

```{plain}
.
├── atdkg                                           ; source code.
│   ├── pom.xml                                     ; dependencies and build configurations.
│   ├── src
│   │   ├── main
│   │   │   ├── java
│   │   │   │   └── org
│   │   │   │       └── ccs24
│   │   │   │           └── atdkg
│   │   │   │               ├── RunAll.java         ; main function of `run.jar`.
│   │   │   │               ├── DkgNoteTest.java    ; main function of `computationTest.jar`.
│   │   │   │               └── ......              ; others communication and computation classes.
│   │   │   └── resources
│   │   └── test                                    ; small functional tests.
│   └── target                                      ; artifacts will be built here and copied to the top level.
├── aws_test_framework                              ; an optional testing helper framework, check its own README.
├── .github
│   └── workflows
│       ├── computation_test.yml                    ; GitHub Action to build `computationTest.jar` and run it using GitHub Action instances.
│       └── main.yml                                ; GitHub Action to build `run.jar` and `computationTest.jar`, and release it with tag `AEC`.
├── .gitignore
├── build.sh                                        ; script to build `run.jar` and `computationTest.jar` manually.
├── run.jar                                         ; executable artifact to conduct end-to-end experiments locally or on the cloud.
├── computationTest.jar                             ; executable artifact to conduct local computation tests.
├── computationtest.sh                              ; bash script to run computation tests for group sizes ranging from $2^{9}$ to $2^{15}$.
├── LICENSE
├── localtest_logs                                  ; test log folder to be created by localtest.sh and
│   └── Repeat01                                    ; note only ONE repeat will be executed.
├── localtest.sh                                    ; spawn (network_size+1) nodes (processes) locally to execute the Any-Trust DKG protocol,
│                                                   ; where the last node will be simulating a blockchain.
│                                                   ; upon completion, test logs can be found in `localtest_logs` directory.
├── log4j.properties                                ; logging specification.
├── README.md
├── analysis_bandwidth.sh                           ; this script analyzes experiment logs and report the max bandwidth usage among all repeats.
├── analysis.sh                                     ; this script analyzes experiment logs and report the worst adjusted running time and
│                                                   ; the max bandwidth usage among all repeats.
├── analyze_results_expected.sh                     ; this script analyzes all raw log data (in `results_expected`) of the experiments mentioned in the paper and
│                                                   ; and report a summary of statistics shown in Figure 3 (a) & (b).
├── analyze_results_expected.example.txt            ; a copy of the results of analyze_results_expected.sh, executed on the author's device.
└── results_expected                                ; the raw log data of the experiments mentioned in the paper.
    ├── GroupSize129-ExpIdcorrupted-0428-atSize38   ; GroupSize is (network_size+1), where network_size is the real number of parties
    │   │                                           ; particated in the protocol and `+1` is the simulated blockchain node.
    │   │                                           ; ExpId[corrupted/normal] states whether half of the nodes are corrupted.
    │   ├── configuration.txt                       ; record the network_size and whether half of the nodes are corrupted.
    │   ├── Repeat1
    │   ├── ......                                  ; each unique experiment configuration is repeated eight times.
    │   └── Repeat8
    │       └── logs
    │           └── stdout
    │               ├── 0.log
    │               ├── ......                      ; logs of key statistics per node, numbered by their indices.
    │               └── 128.log                     ; the 129-th node is the simulated blockchain node.
    ├── GroupSize129-ExpIdnormal-0428-atSize38      ; there are 5*2=10 different configurations, each repeated eight times.
    ├── GroupSize17-ExpIdcorrupted-0428-atSize38
    ├── GroupSize17-ExpIdnormal-0428-atSize38
    ├── GroupSize257-ExpIdcorrupted-0428-atSize38
    ├── GroupSize257-ExpIdnormal-0428-atSize38
    ├── GroupSize33-ExpIdcorrupted-0428-atSize38
    ├── GroupSize33-ExpIdnormal-0428-atSize38
    ├── GroupSize65-ExpIdcorrupted-0428-atSize38
    └── GroupSize65-ExpIdnormal-0428-atSize38
```

## Evaluation Instructions

### Build From Source Code (3 compute-minutes)

Once the [Software Dependencies](#software-dependencies) have been satisfied, the artifacts can be built from source:

```{bash}
bash build.sh
```

The script will download the source of the key library and install it by Maven. The two artifacts, `run.jar` and `computationTest.jar`, will be copied to the top level directory upon a successful build.

### Run Local End-to-end Test (3 compute-minutes per experiment configuration; 500 MB RAM per node)

Note: the original experiments were conducted on **MULTIPLE** AWS EC2 `t3a.medium` instances in the same AWS region.
Each instance has 2 vCPUs and 4 GB RAM and runs in Amazon Linux 2023 AMI 2023.4.20240416.0 x86_64 HVM kernel-6.1 image.
Difference is expected when the testing environment varies.

Due to the limit of budget, we will not provide AWS access to AEC reviewers. Instead, we provide (1) instructions to simulate end-to-end experiments on a (multicore) local machine, (2) analysis of the raw logs in [later section](#analyze-raw-logs-05-compute-minutes), and (3) a copy of our testing framework on AWS in [later section](#run-cloud-based-end-to-end-test-1-2-human-hours-3-5-compute-minutes-per-node-per-repeat-per-experiment-configuration-4-gb-ram-per-ec2-node).

In E2E local simulation, multiple local nodes (processes) are spawn to simulate multiple AWS EC2 instances in our original experiments and each local node may consume about 500 MB RAM. Nevertheless, we acknowledge and emphasize that the local E2E experiment are not equivalent to our original cloud experiments, and results may vary due to differences in:
- hardware specification;
- resource contention;
- network topology.

Consider a PC with 32-core CPU and 32 GB RAM. In our observations, local E2E experiments become compute-bound when the network size reaches 32, since computational tasks arrive each process at almost exactly the same time, causing processes to competing for computation resources. Such contention does not happen when each node resides in an independent EC2 instance. Therefore, when the network size is greater than 32, we do not recommend running experiments locally. Instead, nodes should be properly deployed on cloud (E.g., AWS) instances.

To edit configuration of local E2E test, edit the following parameters in [`localtest.sh`](localtest.sh).

```{bash}
network_size=16
protocol=corrupted
```
Where
- `network_size` refers to the number of nodes participates in the Any-Trust protocol, except for the simulated blockchain node;
- `protocol` will cause half of the nodes to have adversary behavior when it is NOT `normal`.

To run local E2E experiment, one needs to ensure `run.jar` is correctly placed at the top level directory and execute
```{bash}
bash localtest.sh
```

One can download the `run.jar` in [the release](https://github.com/mtc2000/AnyTrustDKG-ArtifactEvaluation/releases/tag/AEC) or [manually build `run.jar` from source code](#build-from-source-code-3-compute-minutes). 


It will prompt the configuration of the experiment and ask you to hit `Enter` to proceed. For example,
```{plain}
8GB RAM recommended to accommodate network_size=16
15GB RAM recommended to accommodate network_size=32
For network_size > 32, we do not recommend running experiments via localtest.sh. Instead, it should be deployed on cloud among multiple instances

current free memory:
               total        used        free      shared  buff/cache   available
Mem:            30Gi        18Gi       4.3Gi       4.1Gi       7.3Gi       7.0Gi
Swap:           31Gi        29Mi        31Gi

network_size: 16
protocol: corrupted
Press Enter to continue... Ctrl-C to abort...
```

Once `Enter` is pressed, it will spawn 17 replicas, and await them to terminates.
Execution logs will be stored in the folder `localtest_logs`.

```{plain}
replica 00000 starts with PID: 541283
replica 00001 starts with PID: 541286
replica 00002 starts with PID: 541289
replica 00003 starts with PID: 541292
replica 00004 starts with PID: 541295
replica 00005 starts with PID: 541298
replica 00006 starts with PID: 541301
replica 00007 starts with PID: 541304
replica 00008 starts with PID: 541307
replica 00009 starts with PID: 541310
replica 00010 starts with PID: 541313
replica 00011 starts with PID: 541316
replica 00012 starts with PID: 541319
replica 00013 starts with PID: 541322
replica 00014 starts with PID: 541325
replica 00015 starts with PID: 541328
replica 00016 starts with PID: 541331
last replica runs as the simulated blockchain node
experiments are running... processes will automatically terminate in about 140 seconds...
Process 541283 has terminated.
Process 541286 has terminated.
Process 541289 has terminated.
Process 541292 has terminated.
Process 541295 has terminated.
Process 541298 has terminated.
Process 541301 has terminated.
Process 541304 has terminated.
Process 541307 has terminated.
Process 541310 has terminated.
Process 541313 has terminated.
Process 541316 has terminated.
Process 541319 has terminated.
Process 541322 has terminated.
Process 541325 has terminated.
Process 541328 has terminated.
Process 541331 has terminated.
All processes have terminated.
Next, run `bash analysis.sh localtest_logs/` to see analysis of logs
```

Finally, run `bash analysis.sh localtest_logs/` to analyze the logs.

```{plain}
analyzing this folder: (!!!omitted path!!!)/localtest_logs/

configuration:
network_size: 16
protocol: corrupted

worst adjusted_running_time and its breakdown by multicast_time and computation_time (in miliseconds)
387,150,237
(worst) of worst bandwidth usage data (in bytes)
25852
```

An example of the `localtest_logs` analysis output is provided at above.
The output of this experiment may validate the following claims:

- **Figure 3 (a) and (b) on page 16**:
    - When the protocol is `corrupted`:
        - `worst adjusted_running_time and its breakdown by multicast_time and computation_time (in miliseconds)` (`387,150,237`) reflects the total runtime of the worst-cast adjusted running time of "bad" instances when the number of nodes is 16, in Figure 3 (a), and a breakdown by multicast (orange solid coloring) and computation (blue solid coloring).
        - `(worst) of worst bandwidth usage data (in bytes)` (`25852`) reflects the bandwidth usage (red data point), in Figure 3 (b), when the number of nodes is 16.
    - When the protocol is `normal`:
        - `worst adjusted_running_time and its breakdown by multicast_time and computation_time (in miliseconds)` is not measured in the paper and should be ignored.
        - `(worst) of worst bandwidth usage data (in bytes)` reflects the bandwidth usage (blue data point), in Figure 3 (b), when the number of nodes is X.

### Run Cloud-based End-to-end Test (0.5-1 human-hours to configure; ~7 compute-minutes per node per repeat per experiment configuration; 4 GB RAM per EC2 node)

We regard this section as an optional since the AWS setup itself is more complicated, expensive, time-consuming, and it requires an intermediate level of knowledge in AWS services. We make the technical details of our attempt public, in case it may benefit the larger community.

First, we estimate the total cost of repeating all our experiments.
The total `t3a.medium` computation time can be estimated by 7 x 8 (repeats) x (16 + 32 + 64 + 128 + 256) x 2 ~= 56000 minutes.
Assume the On-Demand hourly cost is `0.0376` USD, the total cost of running all 8 repeats and 10 different configurations will cost roughly 35 USD. Data transfer among EC2 instances within the same region is free of charge. However, S3 may generate extra cost since logs are uploaded to and downloaded from S3.

If the 10 different configurations (5 scales x 2 protocols (normal/corrupted)) are executed sequentially with each configuration repeating 8 times, the real time to complete all experiments is 10 x 8 x 7 / 60 = 9.3 hours. However, despite repeats cannot be parallelized by our script, configurations can be. Therefore, the minimal parallel computation time can be achieved when all 10 configurations are run in parallel. This realizes a 10x speedup, and 7 x (repeats) 8 = 56 compute-minutes are needed. As a prerequisite, one needs to make sure the EC2 quota limit `us-east-1` region is sufficient (greater than 2000 vCPU cores). The helper script [`setLimit.sh`](aws_test_framework/ec2-provision/preparation/setLimit.sh) may be helpful to increase the quota limit.

Nevertheless, we highlight the risk of running too many configurations or instances in parallel. When some instances have an unexpected delay after provisioning for unknown reason, it might be more difficult to identify the abnormal pending instances among too many parallel configurations. One should consult the helper script in [`aws_test_framework/ec2-provision/miscs/`](aws_test_framework/ec2-provision/miscs/) if they decide to run most configurations in parallel.

Due to the differences in different cloud computing providers, we will NOT provide an out-of-box setup in this section. Instead, we explain how we organize our experiments on AWS, and provide a public version of our testing framework.

We attach the testing framework with documentation in the folder [`aws_test_framework`](aws_test_framework). Note that the provided framework can be reused for any distributed experiments on AWS regardless of software dependencies, therefore it can be of independent interests and uses.

Please refer to the README file at [`aws_test_framework/README.md`](aws_test_framework/README.md) for more details.

### Run Local Computation Test (Heavier computation; 10~20 compute-minutes; 4 GB RAM)

Note: the original experiments were conducted on an AWS EC2 `c5a.large` instance, which equips AMD EYPC 7002 CPU with 2 cores and 4 GB RAM. Difference is expected when the testing environment varies.

One can download the `computationTest.jar` in [the release](https://github.com/mtc2000/AnyTrustDKG-ArtifactEvaluation/releases/tag/AEC) or [manually build `computationTest.jar` from source code](#build-from-source-code-3-compute-minutes). 

When the `computationTest.jar` is correctly placed in the current directory, run the following to start the local computation test for group sizes ranging from $2^{9}$ to $2^{15}$.

```{bash}
bash computationtest.sh
```

The original experiment output is provided at [computationtest.expected.txt](computationtest.expected.txt). Note that class name (`au.edu.sydney.DkgNoteTest`) in the original log is different from the one that this repository will produce (`org.ccs24.atdkg.DkgNoteTest`), since the repository was only anonymized at the submission time. We keep the original log as-is.
Take $2^9$ as an example scale, the output is expected in the following formatting:

```{plain}
INFO: ===Test start===; Nodes: 2^9=====
deal end
Apr 28, 2024 12:35:50 PM au.edu.sydney.DkgNoteTest all
INFO: ===END=== 2^9: DEAL (212ms); GoodVerify (2346 ms); GoodTotal (2558ms); BadVerify (2528ms); BadTotal (2740 ms)
```

The output will validate the following claims:

- **Figure 5 on page 18**:
    - Our-Deal (red solid line), matching `DEAL (...ms);` in the output;
        - The first point ($N=2^9$) on the Our-Deal line corresponds to `212ms`.
    - Our-GoodVerify (apricot solid line), matching `GoodVerify (...ms);` in the output;
        - The first point ($N=2^9$) on the Our-GoodVerify line corresponds to `2346ms`.
    - Our-BadVerify (brown solid line), matching `BadVerify (...ms);` in the output;
        - The first point ($N=2^9$) on the Our-BadVerify line corresponds to `2528ms`.

`GoodTotal` and `BadTotal` are not directly presented in the paper.

If you wish to conduct the test in a scale outside the range $2^{9}$ to $2^{15}$, edit the script [`computationtest.sh`](computationtest.sh) to customize the range and re-run. A larger upper bound is expected to require more compute-minute and more RAM.

```{bash}
java \
 -jar computationTest.jar \
 LOWER_BOUND_POEWR_OF_TWO \
 UPPER_BOUND_POEWR_OF_TWO
```

### Analyze raw logs (0.5 compute-minutes)

To recompute all the numeric results shown in Figure 3 (a) and (b), run the following to analyze raw logs.

```{bash}
bash analyze_results_expected.sh
```

The raw logs and the analysis scripts are static, therefore the output of the script remains static.
We provide the static output at [analyze_results_expected.example.txt](analyze_results_expected.example.txt), where the absolute folder path is omitted.
The output of the analysis was used to generate the following figures:

- **Figure 3 (a) on page 16**
- **Figure 3 (b) on page 16**

## Important Notes for AEC Review

**To facilitate AEC review, we summarize the materials and instructions related to the three badges as follows:**

- Artifacts Available:
This repository includes the source code the software, the raw experiment data and various scripts to analyze the data and to facilitate experiment execution.
All data is available on Zenodo and public in GitHub. See previous section [**Obtain the Artifact**](#obtain-the-artifact).

- Artifacts Evaluated - Functional: Instructions in this document are expected to lead to successful build and execution of the software `run.jar` and `computationTest.jar`.
- Artifacts Evaluated - Reusable: The code base in this artifact can be reused and repurposed, where the key components are listed in the [**introduction**](#introduction). The provided AWS testing framework may be of independent interest and be reused for any distributed experiments on AWS regardless of software dependencies.

- Results Reproduced:
Following the instructions in [**Run Local Computation Test**](#run-local-computation-test-heavier-computation-1020-compute-minutes-4-gb-ram), the computational results in our paper can be generated independently within an allowed tolerance.
Following the instructions in [**Run Local End-to-end Test**](#run-local-end-to-end-test-3-compute-minutes-per-experiment-configuration-500-mb-ram-per-node), the end-to-end experiment results can be generated with some tolerance in a local environment.
Since we will not provide AWS EC2 access to reviewers, the end-to-end experiment on cloud cannot be very easily obtained, though we have provided instructions in [**Run Cloud-based End-to-end Test**](#run-cloud-based-end-to-end-test-05-1-human-hours-to-configure-7-compute-minutes-per-node-per-repeat-per-experiment-configuration-4-gb-ram-per-ec2-node).
