#!/bin/bash
#
#
#

tst=./CodeGen/tst/base/testcases
sol=./CodeGen/tst/base/solutions
ans=./CodeGen/results
report=$ans/report.txt
all=0
ok=0

rm -rf $ans
mkdir -p $ans
echo "Codge Generation Test Report" >$report
echo "generated "`date` >>$report
#
# Run testcases:
#
echo "Testing code generation..."
for file in $tst/*.mc
do
     all=$(( $all + 1 ))
     f=`basename $file .mc`
     rm -f $f.j $f.class
     java -ea MiniC.MiniC $file > /dev/null
     if [ -f $f.j ]
     then
          # We produced $f.j, compile to classfile and run:
          jasmin $f.j > /dev/null
          if [ -f $f.class ]
          then
             java -ea -cp . $f >$ans/res_$f
             diff -u --ignore-all-space --ignore-blank-lines $sol/${f}.txt $ans/res_$f > $ans/diff_$f
             if [ "$?" -eq 0 ]
             then
                 echo -n "+"
                 echo "$f succeded" >> $report
                 rm -rf $ans/res_$f $ans/diff_$f $f.j $f.class
                 ok=$(( $ok + 1 ))
                 continue
             fi
          else
             echo "Could not generate $f.class"
          fi
     else
          echo "Could not generate $f.j"
     fi
     #echo -n "-"
     echo "$f failed" >> $report
     #exit -1
done
echo
echo "Testing finished, pls. consult the test report in $ans."
echo "$ok out of $all testcases succeeded."
