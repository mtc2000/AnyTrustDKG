We regard this framework as experimental, and we assume the reader has an intermediate level of knowledge in AWS services. We make the technical details of it public, in case it may benefit the larger community.

## Ideas

This aim of this framework is to facilitate a more efficient and user-friendly interaction between a user and the AWS infrastructure when deploying distributed experiments on AWS. Specifically, a user can achieve the following goals:

- Goal 1: Provision $N$ instances with the same hardware specification and software dependencies.
- Goal 2: Unique or private parameters can be easily assigned to individual instances.
- Goal 3: All $N$ instances should start executing a distributed protocol almost at the same time.
- Goal 4: Before the start of execution, each instance needs the knowledge of the locations (IP addresses) of all $N$ instances.
- Goal 5: Execution logs of replicas must be uploaded for later analysis. 
- Goal 6: Repeating execution is easy.

We present the high level idea of how this testing framework works:

- AWS CLI tool is used to request provision of EC2 instances.
- AWS EC2 instances assume AWS IAM role, which allows full access to S3.
- EC2 instances will execute a startup script, assigned by the user with the CLI tool. The startup script calls more subroutine scripts to install software dependencies, download binary executable which runs the distributed protocol, and upload logs to AWS S3.
- To make sure instances start executing at the same time, one of the subroutine script [`ready.sh`](fresh-start/OnS3/ready.sh) will coordinate the starting time. When an instance is ready to start, it will first upload an empty file `123.123.123.123` to a specific folder on S3 that is visible to all other instances, where the filename is its IP address. Then, it repeatedly queries the number of file in the folder, until it reaches exactly the expected scale of the current experiment. Finally, it queries all the filename in the folder, and conclude the list of locations of all $N$ instances.

In Any-Trust DKG cloud experiment, we highlight these events in the framework that are more relevant to our experiment.

1. The artifact `run.jar` is pre-built and uploaded to AWS S3.
2. We utilize AWS command-line tool to provision $N$ `t3a.medium` instances in `us-east-1` region. In the request to provision these nodes, metadata of the experiment is attached to the instance in its environment variables (tags). Moreover, the `user-data` of the instance, which is the startup bash script, is specified to install the dependencies and download the `run.jar` stored on AWS S3.
3. Each instance uploads to AWS S3 a special "ready" file naming after its private IP address. Such a file indicates a specific instance is ready to start the protocol.
4. Each instance momentarily queries the list of "ready" files. As soon as it sees exactly $N$ files, it collects all $N$ IP addresses, sort and store them in a contact list file `hosts.config`. It can easily find out its own rank `index` among $N$ instances.
5. Each instance calls the following command, and starts executing the Any-Trust DKG protocol on cloud:
    ```{bash}
    java \
        -Dlog4j.configuration=file:log4j.properties \
        -jar run.jar \
        $i \
        hosts.config \
        $atSize \
        $sid \
        $protocol \
        >> simple-logs/$i.stdout.log \
        2>> simple-logs/$i.stderr.log
    ```
    Note that
    - `i` refers to the rank of an instance's IP in the sorted contact IP addresses list `hosts.config`;
    - `atSize` refers to the Any-Trust committee size, which is 38 on default;
    - `sid` is a pre-defined session ID shared by all instances as an environment variable (instance tag);
    - `protocol` is a user-specified environment variable, and will cause half of the nodes to have adversary behavior when it is NOT `normal`.
6. When an instance finishes executing the protocol, it uploads the execution logs, namely `$i.stdout.log` and `$i.stdout.err` to AWS S3.
7. Go to Step 3 and repeat `t` times, where an environment variable `repeat` evaluates to `t`.

## How to use this framework

First, we need to restore retracted variables.

### Restore retracted variables

We emphasize that credential information has been retracted in these scripts, and they cannot be used out-of-box.
The retracted variables are often prefixed by value `replace`, and they need to be replaced by the user's own private variables.

There are the pointers to these retracted to be replaced and corresponding instructions.

1. Check [`ec2-provision/run/README.md`](ec2-provision/run/README.md) to replace the occurrence of these variables.

```{plain}
subnet-replacethisbyyourownsubnetid
sg-replacethisbyyourownsgid
arn:aws:iam::ReplaceThisIAMByARoleThatAllowsEC2AndS3AccessRole

aws_test_framework/ec2-provision/run/ec2simple-01.json:
   4:         "SubnetId": "subnet-replacethisbyyourownsubnetid",
   8:           "sg-replacethisbyyourownsgid"
  17:         "Arn": "arn:aws:iam::ReplaceThisIAMByARoleThatAllowsEC2AndS3AccessRole"
```

2. Create a S3 bucket with a unique S3 bucket and keep the `Block all public access` setting, preferably in the `user-east-1` region. Replace the following occurrence of `replace-this-by-your-bucket-name` by the newly created bucket's name.

```
aws_test_framework/ec2-provision/run/run.sh:
  44: aws s3 rm s3://replace-this-by-your-bucket-name/replace-this-by-any-name/GroupSize$groupSize-ExpId$experimentId/ --recursive --dryrun
  58: aws s3 rm s3://replace-this-by-your-bucket-name/replace-this-by-any-name/GroupSize$groupSize-ExpId$experimentId/ --recursive
aws_test_framework/ec2-provision/run/user-script-01.sh:
  3: bucketName=replace-this-by-your-bucket-name

aws_test_framework/fresh-start/archive.sh:
  17:     aws s3 cp binary.$1.tar.gz s3://replace-this-by-your-bucket-name

aws_test_framework/logs/download_stdout_log.sh:
   6: aws s3 cp s3://replace-this-by-your-bucket-name/replace-this-by-any-name/ stdout-logs --exclude "**" --include "**/logs/stdout/**" --recursive --dryrun | tee download-comm.log | wc -l
  10: aws s3 cp s3://replace-this-by-your-bucket-name/replace-this-by-any-name/ stdout-logs --exclude "**"  --include "**/logs/stdout/**" --recursive --only-show-errors
```

3. Create an SSH key pair in `us-east-1` region. [This guide](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/create-key-pairs.html#having-ec2-create-your-key-pair) should be helpful. Replace the following occurrence of `ReplaceThisByYourOwnKey` by the newly created key's name.

```
aws_test_framework/ec2-provision/run/run.sh:
  77:         --key-name ReplaceThisByYourOwnKey \
```

4. Think a prefix string that will hold all the experiment log that. This can be a random string. Replace the following occurrence of `replace-this-by-any-name` by the new prefix of your choice.

```
aws_test_framework/fresh-start/OnS3/instance.sh:
  54:     experimentPrefix="replace-this-by-any-name/GroupSize$groupSize-ExpId$experimentId/"$repeatFolder""

aws_test_framework/logs/download_stdout_log.sh:
   6: aws s3 cp s3://replace-this-by-your-bucket-name/replace-this-by-any-name/ stdout-logs --exclude "**" --include "**/logs/stdout/**" --recursive --dryrun | tee download-comm.log | wc -l
  10: aws s3 cp s3://replace-this-by-your-bucket-name/replace-this-by-any-name/ stdout-logs --exclude "**"  --include "**/logs/stdout/**" --recursive --only-show-errors
```

### Upload executable and scripts to S3 

Make sure you have built `run.jar` following the instructions in [Build From Source Code](../README.md#build-from-source-code-3-compute-minutes) or download it from the GitHub release.
We make a copy of the `run.jar` in the directory [`fresh-start/OnS3/artifacts/`](fresh-start/OnS3/artifacts/) and run

```{bash}
cd fresh-start
bash archive.sh # answer y when it prompts you whether to upload to S3
```

More documentation can be found at [`fresh-start/README.md`](fresh-start/README.md).

### Run a configuration on Cloud

Read the documentation at [`ec2-provision/run/README.md`](ec2-provision/run/README.md) and customize the configuration to be run. Then execute `run.sh` with proper parameters.

```{bash}
cd run
bash run.sh <protocol> <expId> <gsize> <atSize> <repeats> <version>
```

### Analyze logs

When all instances in the same configuration terminated, download the logs by

```{bash}
cd logs
bash download_stdout_log.sh
```

And analyze logs using the scripts [`logs/analysis.sh`](logs/analysis.sh) and [`logs/analysis_bandwidth.sh`](logs/analysis_bandwidth.sh). Instructions to use these scripts are documented in [Analyze raw logs](../README.md#analyze-raw-logs-05-compute-minutes).