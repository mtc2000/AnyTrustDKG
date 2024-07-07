#!/bin/bash

# this script takes ~30 seconds to run

echo "Fig. 3: End-to-end Test Results (a) and the red line (bad) in (b) come from the analysis of the following raw logs"
find results_expected/ -maxdepth 1 -mindepth 1 | sort -t '-' -k1,1V | grep corrupted

# batch analysis
find results_expected/ -maxdepth 1 -mindepth 1 | sort -t '-' -k1,1V | grep corrupted | xargs -I{} bash analysis.sh {}

# one-by-one
# bash analysis.sh results_expected/GroupSize17-ExpIdcorrupted-0428-atSize38
# bash analysis.sh results_expected/GroupSize33-ExpIdcorrupted-0428-atSize38
# bash analysis.sh results_expected/GroupSize65-ExpIdcorrupted-0428-atSize38
# bash analysis.sh results_expected/GroupSize129-ExpIdcorrupted-0428-atSize38
# bash analysis.sh results_expected/GroupSize257-ExpIdcorrupted-0428-atSize38

echo "The blue line (good) in Fig. 3: End-to-end Test Results (b) comes from the analysis of the following raw logs"
find results_expected/ -maxdepth 1 -mindepth 1 | sort -t '-' -k1,1V | grep normal

# batch analysis
find results_expected/ -maxdepth 1 -mindepth 1 | sort -t '-' -k1,1V | grep normal | xargs -I{} bash analysis_bandwidth.sh {}

# one-by-one
# bash analysis_bandwidth.sh results_expected/GroupSize17-ExpIdnormal-0428-atSize38
# bash analysis_bandwidth.sh results_expected/GroupSize33-ExpIdnormal-0428-atSize38
# bash analysis_bandwidth.sh results_expected/GroupSize65-ExpIdnormal-0428-atSize38
# bash analysis_bandwidth.sh results_expected/GroupSize129-ExpIdnormal-0428-atSize38
# bash analysis_bandwidth.sh results_expected/GroupSize257-ExpIdnormal-0428-atSize38
