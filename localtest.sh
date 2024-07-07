#!/bin/bash

if [ ! -f run.jar ]; then
  echo run.jar does not exist! test aborted.
  exit 1
fi

network_size=16
size=$(($network_size + 1))
at_size=38
# protocol=normal
protocol=corrupted

echo "8GB RAM recommended to accommodate network_size=16"
echo "15GB RAM recommended to accommodate network_size=32"
echo "For network_size > 32, we do not recommend running experiments via localtest.sh. Instead, it should be deployed on cloud among multiple instances"

echo

echo "current free memory:"
free -hm

echo

log_dir=localtest_logs/Repeat01
rm -rf localtest_logs
mkdir -p $log_dir
printf "network_size: $network_size\nprotocol: $protocol\n" | tee $log_dir/configuration.txt

echo

read -p "Press Enter to continue... Ctrl-C to abort..."

for replica in $(seq -f "%05g" 0 $(($size - 1)))
do
  printf "replica $replica starts with "
  java \
   -Dlog4j.configuration=file:log4j.properties \
   -jar run.jar \
   $replica \
   $size \
   $at_size \
   exp \
   $protocol > $log_dir/replica"$replica".log 2> $log_dir/replica"$replica".err &
  printf 'PID: '
  echo $! | tee -a $log_dir/pid.txt
done

echo "last replica runs as the simulated blockchain node"
echo "experiments are running... processes will automatically terminate in about 140 seconds..."

# Function to check if a process is running
process_running() {
    ps -p $1 > /dev/null 2>&1
    return $?
}

# Read PIDs from file and wait for each one
while IFS= read -r pid || [[ -n "$pid" ]]; do
    if ! [[ "$pid" =~ ^[0-9]+$ ]]; then
        echo "Warning: Invalid PID '$pid'. Skipping."
        continue
    fi
    
    while process_running $pid; do
        sleep 1
    done
    echo "Process $pid has terminated."
done < $log_dir/pid.txt

echo "All processes have terminated."
echo "Next, run \`bash analysis.sh localtest_logs/\` to see analysis of logs"