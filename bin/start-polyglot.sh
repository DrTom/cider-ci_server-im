TORQUEBOX_HOME=/Users/thomas/.immutant/current \
JBOSS_HOME=$TORQUEBOX_HOME/jboss \
JRUBY_HOME=/Users/thomas/.rbenv/versions/jruby-1.7.11 \
JAVA_OPTS="-Xms64m -Xmx4G -XX:MaxPermSize=512m" \
lein immutant run -b '0.0.0.0'
