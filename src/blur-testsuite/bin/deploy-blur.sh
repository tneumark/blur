#!/usr/bin/env bash

# Copyright (C) 2011 Near Infinity Corporation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

bin=`dirname "$0"`
bin=`cd "$bin"; pwd`

. "$bin"/blur-testsuite-config.sh

$bin/stop-blur.sh

ROOT_DIR=$bin/../../../
ssh blur@$BLUR_VM_IP rm -r /home/blur/blur/bin /home/blur/blur/conf /home/blur/blur/lib /home/blur/blur/interface
scp -r $ROOT_DIR/README.md $ROOT_DIR/bin $ROOT_DIR/conf $ROOT_DIR/interface $ROOT_DIR/lib $bin/../conf blur@$BLUR_VM_IP:/home/blur/blur/

$bin/start-blur.sh