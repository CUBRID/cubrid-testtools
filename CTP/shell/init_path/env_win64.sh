#!/bin/sh
# 
# Copyright (c) 2016, Search Solution Corporation. All rights reserved.
# 
# Redistribution and use in source and binary forms, with or without 
# modification, are permitted provided that the following conditions are met:
# 
#   * Redistributions of source code must retain the above copyright notice, 
#     this list of conditions and the following disclaimer.
# 
#   * Redistributions in binary form must reproduce the above copyright 
#     notice, this list of conditions and the following disclaimer in 
#     the documentation and/or other materials provided with the distribution.
# 
#   * Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products 
#     derived from this software without specific prior written permission.
# 
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
# INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
# WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE 
# USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
#

export JAVA_HOME="C:\Program Files\Java\jdk1.6.0_45"
export QA_REPOSITORY="C:\qa_repository"
export MINGW_PATH="C:\mingw64"
export ANT_HOME="C:\ant"

export PATH="/C/cubrid_common":"/C/ant/bin":"C/ant/lib":"/C/mingw64/bin":"/C/mingw64/lib":"/C/mingw64/x86_64-w64-mingw32/lib":"/C/mingw64/libexec/gcc/x86_64-w64-mingw32/4.7.3":"/C/Program Files/Java/jdk1.6.0_45/bin":$PATH


export init_path=`cygpath "$QA_REPOSITORY\lib\shell\common"`
export SHELL_CONFIG_PATH=`cygpath -w "$QA_REPOSITORY/lib/shell/common"`
export CLASSPATH=`cygpath -w "$CUBRID/jdbc/cubrid_jdbc.jar"`\;`cygpath -w "$QA_REPOSITORY/lib/shell/common/commonforjdbc.jar"`\;.

export LD_LIBRARY_PATH=$QA_REPOSITORY/lib/shell/common/commonforc/lib:$LD_LIBRARY_PATH


export LIBRARY_PATH=`cygpath -w "$MINGW_PATH\bin"`\;`cygpath -w "$MINGW_PATH\lib"`\;`cygpath -w "$MINGW_PATH\x86_64-w64-mingw32\lib"`\;`cygpath -w "$MINGW_PATH\libexec\gcc\x86_64-w64-mingw32\4.7.3"`\;.


SETX JAVA_HOME C:\\Program\ Files\\Java\\jdk1.6.0_45 /M
SETX MINGW_PATH C:\\mingw64 /M
