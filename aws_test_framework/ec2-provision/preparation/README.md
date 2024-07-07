Note: Since Any-Trust DKG experiments only ran in one region (`us-east-1`), we did not use these helper scripts except the `setLimit.sh`. However, we have utilized them in other projects, therefore we share them for independent interests.

Scripts in this folder are useful when identical EC2 instances need to be deployed across different regions around the world.

1. `regions.txt` specifies target regions.
2. `batchCopyAmis.sh` helps copy ONE AMI image to target regions.
3. `batchCreateSGs.sh` helps create identical Security Groups (SGs) in target regions.
4. `checkAmis.sh` save AMI image ID in target regions, given a specific AMI name.
5. `importKeys.sh` imports a public key to target regions.
6. `setLimit.sh` increases the limit of EC2 CPU core limitation in target regions.

When deployment is only needed in ONE region, these scripts are not needed, except for `setLimit.sh`.
The user must manually complete the `amis.csv` to specify the only AMI image to be used.
