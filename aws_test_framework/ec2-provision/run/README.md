Scripts in this folder attempt to provision multiple EC2 instances within the same region or among multiple regions.

1. `regions.txt` specifies target regions. In Any-Trust DKG, this only contains one region `us-east-1`.
2. `run.sh` accepts at most six arguments, which are
    - (1) protocol. This was set to either `normal` or `corrupted`.
    - (2) experiment identifier. This was set to some random prefix.
    - (3) group size. This was set to `[16, 32, 64, 128, 256]`.
    - (4) Any-Trust committee size. This was set to `38`.
    - (5) number of times to repeat this experiment. This was set to `8`.
    - (6) which version of script to use. Use `01` on default. See `ec2simple-01.json` and `user-script-01.sh`.

    This script will provision (group size) of EC2 replicas evenly in target regions.
    In Any-Trust DKG, only `us-east-1` region is targeted, hence all (group size) instances reside in this region.
    To configure the EC2 specification, one needs to edit `ec2simple-01.json`.
    To configure the startup script of each replica, one needs to edit `user-script-01.sh`.
    On default, most of the arguments to `run.sh` will be passed on as environment variables (tags) to each EC2 instanced. To re-configure this behavior, edit `run.sh`.
3. `user-script-01.sh` is the startup script. Every EC2 instance provisioned by `run.sh` will use this script when the version number is `01`.

    Note that this script downloads and calls subsequent scripts in the [`fresh-start`](../../fresh-start/) folder, since all files and folders within the [`OnS3`](../../fresh-start/OnS3/) folder, except the folder itself, will be archived by [`archive.sh`](../../fresh-start/archive.sh) and uploaded to S3.
4. `ec2simple-01.json` specifies the type of instance and network interface of the instance. The following three fields in the JSON file need to be customized by the user:
    - `SubnetId`: `subnet-replacethisbyyourownsubnetid` needs to be replaced by a valid subnet ID.
    - `Groups`: `sg-replacethisbyyourownsgid` needs to be replaced by a valid Security Group ID.
    - `Arn`: `arn:aws:iam::ReplaceThisIAMByARoleThatAllowsEC2AndS3AccessRole` needs to be replaced by a valid IAM Role ID, where the following permissions policies have been attached to the role:
        - [`AmazonEC2ReadOnlyAccess`](https://docs.aws.amazon.com/aws-managed-policy/latest/reference/AmazonEC2ReadOnlyAccess.html): This standard policy is managed by AWS. When the EC2 instance assumes this access, it can read its own metadata as well as other EC2 instances' metadata.
        - [`AmazonS3FullAccess`](https://docs.aws.amazon.com/aws-managed-policy/latest/reference/AmazonS3FullAccess.html): This standard policy is managed by AWS. When the EC2 instance assumes this access, it can read and write to any S3 bucket. This allows us to:
            - upload, count and download the `ready` files to S3;
            - upload execution logs to S3.
5. `logs/` is a folder that contains auxiliary data after executing `run.sh`.