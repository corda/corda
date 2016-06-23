import groovy.xml.MarkupBuilder
import groovy.xml.StreamingMarkupBuilder


println "Hello, Script!!!"



def fragments = new XmlParser(false, true).parseText("<html><body></body></html>")

def resultFile = new File('/home/mlutescu/0_WORK/SGXUserGuide/developer_guide_eclipse_plugin/Output/WebHelp/Content/test.html')

if (resultFile.exists()) {
    resultFile.delete()
}

def fltoc = new File('/home/mlutescu/0_WORK/SGXUserGuide/developer_guide_eclipse_plugin/Project/TOCs/Master.fltoc')
def toc = (new XmlParser()).parse(fltoc)
def contentDir = new File(fltoc, '../../../').canonicalPath

def PrintWriter pw = new PrintWriter(new FileWriter(resultFile))


toc.TocEntry['**'].each { tocEntry ->
    def htmlFile = new File(contentDir, "${tocEntry.'@Link'}")


    println htmlFile.text
}



println groovy.xml.XmlUtil.serialize( fragments)

