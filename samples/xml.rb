#
# This example transform a xml file with a xslt stylesheet
# to an output xml.
#

# Load the Java classes.

# Edit the path to point to your saxon dir:
# require "saxon.jar"

Java::import "javax.xml.transform.stream"
Java::import "javax.xml.transform"

Java::name "java.io.File", "JavaFile"


xml = "./samples/birds.xml"
xslt = "./samples/birds.xsl"
output = "./samples/birds.html"

xml_file = JavaFile.new xml
xslt_file = JavaFile.new xslt
output_file = JavaFile.new output

xml_source = StreamSource.new xml_file
xslt_source = StreamSource.new xslt_file
output_result = StreamResult.new output_file

factory = TransformerFactory.newInstance

transformer = factory.newTransformer xslt_source
transformer.transform xml_source, output_result

puts "XML file transformed."
