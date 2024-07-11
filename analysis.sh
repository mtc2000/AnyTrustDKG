#!/bin/bash

experiment_folder=$(pwd)/$1

if [ ! -d "$experiment_folder" ]; then
   echo $experiment_folder is not a dir
   echo plz check again
   exit 1
fi

echo analyzing this folder: $experiment_folder
cd $experiment_folder

echo
echo configuration:
find $experiment_folder -type f -name "configuration.txt" | xargs cat
echo

echo "worst adjusted_running_time and its breakdown by multicast_time and computation_time (in miliseconds)"

for repeat in $(find $experiment_folder -type d -name "Repeat*");
do
   for log_file in $(find $repeat -type f -name "*.log");
   do
      # echo $log_file
      if [ $(grep -ic 'broadcastBlockchainRelay' "$log_file") -ge 1 ]; then
         continue
      fi

      multicast_send_time=$(grep -A1 '^time to send multicast complaint$' "$log_file" | tail -n1)
      multicast_receive_time=$(grep -A1 '^time to receive multicast complaint$' "$log_file" | tail -n1)
      multicast_time=$(($multicast_send_time + $multicast_receive_time))
      computation_time=$(grep -A1 '^total computation cost:$' "$log_file" | tail -n1)
      adjusted_running_time=$(($multicast_time + $computation_time))
      echo $adjusted_running_time,$multicast_time,$computation_time
   done | sort -t',' -k 1,1nr | head -n1
done | sort -t',' -k 1,1nr | head -n1

echo "(worst) of worst bandwidth usage data (in bytes)"

for repeat in $(find $experiment_folder -type d -name "Repeat*");
do
   for log_file in $(find $repeat -type f -name "*.log");
   do
      # echo $log_file
      if [ $(grep -ic 'broadcastBlockchainRelay' "$log_file") -ge 1 ]; then
         continue
      fi

      grep -A1 '^total data:$' "$log_file" | tail -n1
   done
done | datamash -W --output-delimiter=',' max 1 # mean 1 # uncomment this if you wish to see the (mean) of the worst bandwidth over repeats

echo
