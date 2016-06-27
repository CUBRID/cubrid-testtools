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
