#!/bin/bash

mkdir ./Results/$1
tst=./Parser/tst/base/AST_testcases
sol=./Parser/tst/base/AST_solutions_trees
unp=./Parser/tst/base/AST_solutions_unparsed
ans=./Results/$1
report=$ans/report.txt
all=0
ok=0

echo "AST Test Report" >$report
echo "generated "`date` >>$report
#
# Run testcases:
#
echo "Testing the parser..."
for file in $tst/c*.mc
do
    all=$(( $all + 2 ))
    f=`basename $file`
    java MiniC.MiniC -t $ans/$f.ast $file > $ans/$f.ast.report 2>&1
    diff -u --ignore-all-space --ignore-blank-lines $ans/$f.ast $sol/$f.ast > $ans/diff_$f.ast 2>&1
    if [ "$?" -ne 0 ]
    then
        echo -n "-"
        echo "$f AST failed" >> $report

    else
        echo -n "+"
        echo "$f AST succeded" >> $report
        rm -rf $ans/diff_$f.ast $ans/$f.ast $ans/$f.ast.report
        ok=$(( $ok + 1 ))
    fi

    java MiniC.MiniC -u $ans/$f.u $file > $ans/$f.u.report 2>&1
    diff -u --ignore-all-space --ignore-blank-lines $ans/$f.u $unp/$f.u > $ans/diff_$f.u 2>&1
    if [ "$?" -ne 0 ]
    then
        echo -n "-"
        echo "$f Unparse failed" >> $report

    else
        echo -n "+"
        echo "$f Unparse succeded" >> $report
        rm -rf $ans/diff_$f.u $ans/$f.u $ans/$f.u.report
        ok=$(( $ok + 1 ))
    fi
done
echo
echo "Testing finished, pls. consult the test report in $ans."
echo "$ok out of $all testcases succeeded."
