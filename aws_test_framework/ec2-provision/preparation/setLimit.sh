#!/bin/bash

# This script request the On-Demand Standard quotas to be increased to 60 for all regions in `regions.txt`

# Check if the regions file exists
if [ ! -f "regions.txt" ]; then
    echo "Error: regions.txt file not found."
    exit 1
fi

# Read regions from the file into an array
target_regions=($(cat regions.txt))
target_quotas=60

# Loop through target regions and copy AMI if source and target regions are different
for region in "${target_regions[@]}"; do
    service_code=$(aws service-quotas list-service-quotas --service-code ec2 --region us-east-1 --query 'Quotas[*].[QuotaName,QuotaCode]' --output text | tr '\t' '|' | grep 'On-Demand Standard' | cut -d'|' -f2)
    echo service code is $service_code
    aws service-quotas request-service-quota-increase \
    --region $region \
    --service-code ec2 \
    --quota-code $service_code \
    --desired-value $target_quotas
done
