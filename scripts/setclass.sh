#! /bin/bash

for i in `echo $CLASSPATH | sed "s\:\ \g"`
do
mv $i/MiniC/MiniC.java $i/MiniC/MiniC.java.bak
mv $i/MiniC/Scanner/Scanner.java $i/MiniC/Scanner/Scanner.java.bak
cp /opt/ccugrad/Assignment2/Scanner.class $i/MiniC/Scanner/
cp /opt/ccugrad/Assignment2/MiniC.class $i/MiniC/
done
