[![DOI](https://zenodo.org/badge/828109644.svg)](https://zenodo.org/doi/10.5281/zenodo.12736462)

> *Scalable and Adaptively Secure Any-Trust Distributed Key Generation and All-hands Checkpointing* Accepted by [CCS'24](https://www.sigsac.org/ccs/CCS2024).

## Introduction

This codebase contains a proof-of-concept implementation and experiment results of Any-Trust DKG in the paper [*Scalable and Adaptively Secure Any-Trust Distributed Key Generation and All-hands Checkpointing*](ccs2024b-paper583.accepted-version.pdf).

Further documentations can be found at the [artifact evaluation repository](https://github.com/mtc2000/AnyTrustDKG-ArtifactEvaluation/).

Core components of the implementation are as follows:
- [Verifiable random function](atdkg/src/main/java/org/ccs24/atdkg/VRF.java)
- [Multi-recipient encryption](atdkg/src/main/java/org/ccs24/atdkg/VEnc.java)
- [Point-to-point synchronized communication library](atdkg/src/main/java/org/ccs24/atdkg/DataPacketTimeoutBuffer.java) allowing timeout, based on [mpc4j-v1.1.1](https://github.com/alibaba-edu/mpc4j/releases/tag/v1.1.1)

### Software Dependencies

- Java 17. Recommend installing use [sdkman](https://sdkman.io/install).
- Maven 3. Recommend installing use [sdkman](https://sdkman.io/install).
- Key library used: [mpc4j-v1.1.1](https://github.com/alibaba-edu/mpc4j/releases/tag/v1.1.1)
- (For evaluation only) [GNU data mash](https://www.gnu.org/software/datamash/download/), allowing easy arithmetic on experiment log statistics.

This implementation has been successfully tested on

- [Amazon Linux 2023 AMI **2023.4.20240416.0** x86_64](https://docs.aws.amazon.com/linux/al2023/release-notes/relnotes-2023.4.20240416.html) with kernel version `6.1.84-99.169.amzn2023`.
- Ubuntu **22.04.4 LTS** (x86_64) with kernel version `5.15.0-113-generic`.
- [GitHub Actions Runner Images Ubuntu **22.04.4 LTS** 20240708.1.0](https://github.com/actions/runner-images/blob/1b535372a075188bfce11fbd27d72a254c736b3c/images/ubuntu/Ubuntu2204-Readme.md) with kernel version `6.5.0-1023-azure`.

This implementation does not use or require GPU/CUDA.

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

## Build From Source Code (3 compute-minutes)

Once the [Software Dependencies](#software-dependencies) have been satisfied, the artifacts can be built from source:

```{bash}
bash build.sh
```

The script will download the source of the key library and install it by Maven. The two artifacts, `run.jar` and `computationTest.jar`, will be copied to the top level directory upon a successful build.

## Evaluation Instructions And More

Please review the [artifact evaluation repository](https://github.com/mtc2000/AnyTrustDKG-ArtifactEvaluation/).
