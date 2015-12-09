#!/bin/bash

mkdir ./Results/$1
tst=./SemanticAnalysis/tst/base/testcases
sol=./SemanticAnalysis/tst/base/solutions
ans=./Results/$1
report=$ans/report.txt
all=0
ok=0

echo "AST Test Report" >$report
echo "generated "`date` >>$report
#
# Run testcases:
#
echo "Testing the semantic analyser..."
for file in $tst/c*.mc
do
    all=$(( $all + 1 ))
    f=`basename $file`
    java MiniC.MiniC $file > $ans/$f.ans 2>&1
    diff -u --ignore-all-space --ignore-blank-lines $ans/$f.ans $sol/$f.sol > $ans/diff_$f.txt 2>&1
    if [ "$?" -ne 0 ]
    then
        echo -n "-"
        echo "$f AST failed" >> $report

    else
        echo -n "+"
        echo "$f AST succeded" >> $report
        rm -rf $ans/diff_$f.txt $ans/$f.ans
        ok=$(( $ok + 1 ))
    fi
done
echo
echo "Testing finished, pls. consult the test report in $ans."
echo "$ok out of $all testcases succeeded."
