current-version:
	@xmllint --xpath "*[local-name()='project']/*[local-name()='version']/text()" pom.xml | perl -ple 's/-SNAPSHOT//'

start-release:
	rel=$(shell $(MAKE) current-version); git flow release start $$rel
	perl -pi -e 's/-SNAPSHOT//' pom.xml

finish-release:
	rel=$(shell git branch --show-current | cut -d/ -f2); git flow release finish $$rel  -m "Release $$rel" </dev/null
	newrel=$(shell $(MAKE) current-version | perl -pi -e '$$"=".";@v=split/\./;$$v[@v-1]++if@v;$$_="@v";'); \
	perl -pi -e 's,<version>$(shell $(MAKE) current-version)</version>,<version>'"$$newrel"'-SNAPSHOT</version>,' pom.xml
	git commit pom.xml -m 'back to snapshot'

push:
	git remote -v | awk '{print$$1}' | uniq | while read remote; do git push $$remote --all && git push $$remote --tags; done
