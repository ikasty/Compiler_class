#!/bin/bash

tst=./Scanner/tst/base/testcases
sol=./Scanner/tst/base/solutions
ans=./Scanner/tst/base/answer

mkdir -p $ans
for file in $tst/c*.txt
do
     f=`basename $file`
     echo -n "."
     java MiniC.MiniC $file > $ans/s_$f
     diff -u --ignore-all-space --ignore-blank-lines $ans/s_$f $sol/s_$f > /dev/null
     if [ "$?" -eq 1 ]
     then
		 echo
		 echo -e "\nfile $f ..."
		 echo -e "test failed\n"
		 exit
     fi
done

echo -e "\nTest complete. All test-cases succeeded."
