import xml.dom.minidom as md 
import sys

def main():
    filename = sys.argv[1] 
    print(filename)
    logback = md.parse(filename)

    # Insert the logger node under configuration:
    #
    # <configuration debug="false">
    #     <logger name="approveDeny">
    #         <level value="debug"/>
    #     </logger>

    config = logback.getElementsByTagName("configuration")[0]
    contextListener = config.getElementsByTagName("contextListener")

    logger = logback.createElement("logger")
    attrs = logger.attributes
    attrs["name"] = "approveDeny"
    level = logback.createElement("level")
    attrs = level.attributes
    attrs["value"] = "debug"

    config.insertBefore(logger, contextListener[0])
    logger.appendChild(level)

    with open(filename, "w") as f:
        f.write(config.toxml())

if __name__=="__main__": 
    main(); 


 