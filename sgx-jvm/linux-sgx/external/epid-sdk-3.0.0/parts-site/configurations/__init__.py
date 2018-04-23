############################################################################
# Copyright 2016 Intel Corporation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
############################################################################*/
# pylint: disable=locally-disabled, missing-docstring, no-member, unused-variable

import parts.load_module as load_module
import parts.reporter as reporter

def configuration(type_name):
    """define configurations"""
    try:
        mod = load_module.load_module(
            load_module.get_site_directories('configurations'), type_name, 'configtype')
    except ImportError:
        reporter.report_error('configuration "%s" was not found.'%type_name, show_stack=False)
