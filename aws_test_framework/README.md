

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