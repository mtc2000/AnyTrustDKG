Scripts within the [`OnS3`](OnS3/) folder eventually will be loaded to each EC2 instances and called by the startup script [`user-script-01.sh`](../ec2-provision/run/run.sh).

- `archive.sh`: Files and folders within the [`OnS3`](OnS3/) folder, except the folder itself, will be archived by [`archive.sh`](archive.sh).
    Assume the version is `01`, the archived file is `binary.01.tar.gz` and is then uploaded to S3.
- `user-script.sh.example` is a copy of the startup script [`user-script-01.sh`](../ec2-provision/run/run.sh). Users can customize their own script based on this example.
- `OnS3/instance.sh` will be called by the startup script first. Subsequently, it calls the following scripts and may repeat depending on the parameter.
    - `OnS3/dependency.sh` to install software dependencies, such as Java 17 and `tc`, the Unix traffic control tool;
    - `OnS3/ready.sh` to upload its own IP address to S3 indicating it is ready, and wait indefinitely until all $N$ instances have indicated their availability;
    - `OnS3/experiment.sh` to execute the Any-Trust protocol, wait until termination, and upload execution logs to S3.
- `OnS3/artifacts/log4j.properties` is an auxiliary log4j configuration file.
- `OnS3/artifacts/run.jar` should be pre-compiled following the instructions in [Build From Source Code](../README.md#build-from-source-code-3-compute-minutes) or download it from the GitHub release. Copy `run.jar` to this file path.