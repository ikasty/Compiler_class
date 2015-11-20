#!/bin/bash

mkdir ./Results/$1
tst=./Parser/tst/base/testcases
sol=./Parser/tst/base/solutions
ans=./Results/$1
report=$ans/report.txt
all=0
ok=0

echo "Parser Test Report" >$report
echo "generated "`date` >>$report
#
# Run testcases:
#
TMP_SOL=__tmp_sol.txt
TMP_ANS=__tmp_ans.txt
echo "Testing the parser..."
for file in $tst/c*.txt
do
     all=$(( $all + 1 ))
     f=`basename $file`
     java MiniC.MiniC $file > $ans/s_$f
     grep 'Compilation was' $ans/s_$f > $TMP_SOL
     grep 'Compilation was' $sol/s_$f > $TMP_ANS
     diff -u --ignore-all-space --ignore-blank-lines $TMP_ANS $TMP_SOL > $ans/diff_$f
     if [ "$?" -ne 0 ]
     then
		 echo -n "-"
                 echo "$f failed" >> $report

     else
                 echo -n "+"
                 echo "$f succeded" >> $report
                 rm -rf $ans/diff_$f $ans/s_$f
                 ok=$(( $ok + 1 ))
     fi
     rm -f $TMP_SOL $TMP_ANS
done
echo
echo "Testing finished, pls. consult the test report in $ans."
echo "$ok out of $all testcases succeeded."
