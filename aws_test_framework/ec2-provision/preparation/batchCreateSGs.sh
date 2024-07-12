#!/bin/bash

# This script create identifical security groups with name `ReplaceThisByYourOwnSecurityGroupName` in all regions in `regions.txt`
# The SG will allow all inbound traffic for all TCP ports from 0.0.0.0/0 (anywhere)

# Check if the regions file exists
if [ ! -f "regions.txt" ]; then
    echo "Error: regions.txt file not found."
    exit 1
fi

# Read regions from the file into an array
regions=($(cat regions.txt))
group_name="ReplaceThisByYourOwnSecurityGroupName"

# Iterate over each region
for region in "${regions[@]}"; do
    # Get the list of security group IDs with the specified name
    group_ids=($(aws ec2 describe-security-groups \
        --group-names $group_name \
        --region $region \
        --query 'SecurityGroups[*].GroupId' \
        --output text))

    # Iterate over each security group ID and delete it
    for group_id in "${group_ids[@]}"; do
        echo "Deleting existing security group with ID: $group_id" >&2
        aws ec2 delete-security-group --group-id $group_id --region $region > /dev/null
    done

    # echo "Creating security group in region: $region"

    # Create security group
    group_id=$(aws ec2 create-security-group \
        --group-name $group_name \
        --description "Allow all TCP traffic" \
        --region $region \
        --query 'GroupId' \
        --output text)

        # --vpc-id your-vpc-id \
        # specify vpc-id if needed

    #echo "Security group created with ID: $group_id"

    # Authorize inbound traffic for all TCP ports from 0.0.0.0/0 (anywhere)
    aws ec2 authorize-security-group-ingress \
        --group-id $group_id \
        --protocol tcp \
        --port 0-65535 \
        --cidr 0.0.0.0/0 \
        --region $region > /dev/null

    #echo "Inbound traffic authorized for all TCP ports in region: $region"

    echo $region,$group_id
done | tee security-groups.csv

# this script will create a CSV file (security-groups.csv) with contents similar to the following:

# us-east-1,sg-xxxxxxxxxxxxxxxxx
# us-east-2,sg-xxxxxxxxxxxxxxxxx
# us-west-1,sg-xxxxxxxxxxxxxxxxx
# us-west-2,sg-xxxxxxxxxxxxxxxxx
# ca-central-1,sg-xxxxxxxxxxxxxxxxx
# ap-south-1,sg-xxxxxxxxxxxxxxxxx
# ap-northeast-1,sg-xxxxxxxxxxxxxxxxx
# ap-northeast-2,sg-xxxxxxxxxxxxxxxxx
# ap-northeast-3,sg-xxxxxxxxxxxxxxxxx
# ap-southeast-1,sg-xxxxxxxxxxxxxxxxx
# ap-southeast-2,sg-xxxxxxxxxxxxxxxxx
# eu-central-1,sg-xxxxxxxxxxxxxxxxx
# eu-west-1,sg-xxxxxxxxxxxxxxxxx
# eu-west-2,sg-xxxxxxxxxxxxxxxxx
# eu-west-3,sg-xxxxxxxxxxxxxxxxx
# eu-north-1,sg-xxxxxxxxxxxxxxxxx
# sa-east-1,sg-xxxxxxxxxxxxxxxxx
