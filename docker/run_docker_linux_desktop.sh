#!/bin/sh

#Get the USER
baseuser=`id -u`

#Get the Group
basegroup=`id -g`

#WHo are we running as
echo Running as .. $(id -un)

#Create a new folder - as current user
mkdir ~/minidocker

#Remove the old container
sudo docker rm minima

#Start her up in interactive mode
sudo docker run -it -e minima_desktop=true -e minima_mdspassword=123 -p 9001-9004:9001-9004 -d --restart unless-stopped --user $baseuser:$basegroup -v ~/minidocker:/home/minima/data --name minima minimaglobal/minima:latest