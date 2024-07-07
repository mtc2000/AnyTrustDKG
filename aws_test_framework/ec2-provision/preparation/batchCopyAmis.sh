#!/bin/bash

# This script will replicate the `source_ami_id` AMI in the `source_region` to all regions in `regions.txt`
# The replicated AMI name `ami_name` in all regions will be identifical, however the `new_ami_id` in each region will be different

# Replace these variables with your actual values
source_region="us-east-1"
source_ami_id="ami-ReplaceThisByYourOwnAmi"
ami_name="ReplaceThisByYourOwnAmiName"

# Check if the regions file exists
if [ ! -f "regions.txt" ]; then
    echo "Error: regions.txt file not found."
    exit 1
fi

# Read regions from the file into an array
target_regions=($(cat regions.txt))

# Function to check if the source and target regions are the same
function is_same_region() {
    local source="$1"
    local target="$2"

    if [ "$source" == "$target" ]; then
        return 0  # Regions are the same
    else
        return 1  # Regions are different
    fi
}

# Loop through target regions and copy AMI if source and target regions are different
for target_region in "${target_regions[@]}"; do
    if is_same_region "$source_region" "$target_region"; then
        # echo "Skipping copy to $target_region as source and target regions are identical."
        new_ami_id=$source_ami_id
    else
        # echo "Copying AMI to $target_region..."
        new_ami_id=$(aws ec2 copy-image \
        --no-dry-run \
        --name "$ami_name" \
        --source-region "$source_region" \
        --source-image-id "$source_ami_id" \
        --region "$target_region" \
        --query 'ImageId' \
        --output text)
        # echo "Copy to $target_region completed."
    fi
    echo $target_region,$new_ami_id
done | tee amis.csv

# this script will create a CSV file (amis.csv) with contents similar to the following:

# us-east-1,ami-xxxxxxxxxxxxxxxxx
# us-east-2,ami-xxxxxxxxxxxxxxxxx
# us-west-1,ami-xxxxxxxxxxxxxxxxx
# us-west-2,ami-xxxxxxxxxxxxxxxxx
# ca-central-1,ami-xxxxxxxxxxxxxxxxx
# ap-south-1,ami-xxxxxxxxxxxxxxxxx
# ap-northeast-1,ami-xxxxxxxxxxxxxxxxx
# ap-northeast-2,ami-xxxxxxxxxxxxxxxxx
# ap-northeast-3,ami-xxxxxxxxxxxxxxxxx
# ap-southeast-1,ami-xxxxxxxxxxxxxxxxx
# ap-southeast-2,ami-xxxxxxxxxxxxxxxxx
# eu-central-1,ami-xxxxxxxxxxxxxxxxx
# eu-west-1,ami-xxxxxxxxxxxxxxxxx
# eu-west-2,ami-xxxxxxxxxxxxxxxxx
# eu-west-3,ami-xxxxxxxxxxxxxxxxx
# eu-north-1,ami-xxxxxxxxxxxxxxxxx
# sa-east-1,ami-xxxxxxxxxxxxxxxxx
