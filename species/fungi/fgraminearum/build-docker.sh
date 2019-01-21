#!/bin/bash

IMAGE_NAME="knetminer-barley"

cp ../common/quickstart/Dockerfile-local .

docker image build \
    	--build-arg tax_id=7227  \
    	--build-arg species_name="Drosophila melanogaster" \
    	--build-arg species_link_name=drosophila \
    	--build-arg keywords="drosophila, d.melanogaster, knetminer, quickstart, demo" \
    	--build-arg description="Drosophila Knetminer" \
    	--build-arg reference_genome=true \
  --build-arg git_branch=`git branch | grep \* | cut -d ' ' -f2` \
  --build-arg oxl_file_name="DrosophillaKNET.oxl" \
  --build-arg knetminer_port=8080 \
  --squash -t $IMAGE_NAME \
  -f Dockerfile-local .

if [ $? -eq 0 ]; then
	rm Dockerfile-local
	echo "You can run this Docker using: docker run -p8080:8080 -it --rm $IMAGE_NAME"
	echo "Then access it at http://localhost:8080/client/"
	echo "Note: port 8080 is the Tomcat default; replace with the knetminer_port defined in this file"
fi
