#!/bin/bash
#
# NB: this script assumes that each solution file contains
#     zero or one error messages!
#
#

tst=./SemanticAnalysis/tst/base/testcases
sol=./SemanticAnalysis/tst/base/solutions
ans=./SemanticAnalysis/results
report=$ans/report.txt
all=0
ok=0

rm -rf $ans
mkdir -p $ans
echo "Semantic Analysis Test Report" >$report
echo "generated "`date` >>$report
#
# Run testcases:
#
echo "Testing semantic analysis (including bonus assignment)..."
for file in $tst/*.mc
do
     all=$(( $all + 1 ))
     f=`basename $file`
     java -ea MiniC.MiniC $file|grep ERROR|cut -d: -f2|sort> $ans/restmp_$f
     cat $sol/$f.sol|grep ERROR|cut -d: -f2|sort> $ans/sol_$f
     grep -f $ans/sol_$f $ans/restmp_$f|sort -u> $ans/res_$f
     diff -u --ignore-all-space --ignore-blank-lines $ans/sol_$f $ans/res_$f > $ans/diff_$f
     if [ "$?" -ne 0 ]
     then
		 echo -n "-"
                 echo "$f failed" >> $report
                 #exit -1
     else
                 echo -n "+"
                 echo "$f succeded" >> $report
                 rm -rf $ans/diff_$f $ans/sol_$f $ans/restmp_$f $ans/res_$f
                 ok=$(( $ok + 1 ))
     fi
done
echo
echo "Testing finished, pls. consult the test report in $ans."
echo "$ok out of $all testcases succeeded."
