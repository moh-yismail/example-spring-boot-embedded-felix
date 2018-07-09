package com.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.felix.framework.FrameworkFactory;
import org.apache.felix.main.Main;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service	
public class FelixService {
	
	private static final Logger logger = LoggerFactory.getLogger(FelixService.class);
	private static final String SHUTDOWN_HOOK_PROP = "felix.shutdown.hook";
	
	@Autowired
	SpringBootFelixProperties felixProps;
	
	private Framework framework = null;
	
	public FelixService() {
		// Start the Felix framework once all the beans have been created
		// So that we get our property values injected and available
	    logger.debug("***** FelixService Component Constructed *****");
	}
	
	@EventListener(ApplicationReadyEvent.class)
	void startFramework() {
		
		//File absoluteBundleDir = new File(Paths.get("").toAbsolutePath().toString(),bundleDir);
		
		// Setup the properties required by Felix
		Properties frameworkProps = felixProps.getProperties();
		loadFelixProperties(frameworkProps);
		
		logger.info("***** Felix Framework STARTING *****");
		logger.info("***** Felix Framework : Loading bundles from "+frameworkProps.get("felix.auto.deploy.dir")+" *****");

	    addShutdownHook(framework,frameworkProps);
	    
	    try
	    {
	        // Create an instance and initialize the framework.
	        FrameworkFactory factory = getFrameworkFactory();
	        framework = factory.newFramework(frameworkProps);
	        framework.init();
	        
	        // Use the system bundle context to process the auto-deploy
	        // and auto-install/auto-start properties.
	        AutoProcessor.process(frameworkProps, framework.getBundleContext());
	        
	        // Start the framework.
	        framework.start();
	        // Log Bundle Activations
	        framework.getBundleContext().addBundleListener(new FelixBundleListener());
	        // Wait for framework to stop -- then exit the VM.
	        logger.info("***** Felix Framework STARTED : listening for shutdown hook *****");
	        framework.waitForStop(0);
	        System.exit(0);
	    }
	    catch (Exception ex)
	    {
	    	logger.error("Could not create framework: " + ex);
	        ex.printStackTrace();
	        System.exit(0);
	    }
	}
	
	private void loadFelixProperties(Properties frameworkProps) {
	    Map<String, String> felixConfigMap = Main.loadConfigProperties();
	    
	    if(felixConfigMap != null) {
	    	frameworkProps.putAll(felixConfigMap);
	    }
	    
	    //configProps.setProperty(AUTO_DEPLOY_DIR_PROPERTY, absoluteBundleDir.toString());
	    //configProps.setProperty(AUTO_DEPLOY_ACTION_PROPERTY, bundleActions);
	    //configProps.setProperty(Constants.FRAMEWORK_STORAGE, cacheDir);
	    
	    if (frameworkProps.isEmpty())
	    {
	    	logger.error("***** FelixService : No felix.properties found. Initializing with EMPTY props *********");
	    }
	    Main.copySystemProperties(frameworkProps);
		
	}

	private void addShutdownHook(Framework framework, Properties configProps) {
		
		// Add a shutdown hook to clean stop the framework.
	    String enableHook = configProps.getProperty(SHUTDOWN_HOOK_PROP);
	    if ((enableHook == null) || !enableHook.equalsIgnoreCase("false"))
	    {
	        Runtime.getRuntime().addShutdownHook(new Thread("Felix Shutdown Hook") {
	            public void run()
	            {
	            	logger.info("***** Felix Framework STOPPING : shutdown hook detected*****");
	                try
	                {
	                    if (framework != null)
	                    {
	                    	framework.stop();
	                    	framework.waitForStop(0);
	                    	logger.info("***** Felix Framework STOPPED *****");
	                    }
	                }
	                catch (Exception ex)
	                {
	                    System.err.println("Error stopping framework: " + ex);
	                }
	            }
	        });
	    }
	    
	}
	
	private FrameworkFactory getFrameworkFactory() throws Exception
    {
        java.net.URL url = FelixService.class.getClassLoader().getResource(
            "META-INF/services/org.osgi.framework.launch.FrameworkFactory"
        );
        if (url != null)
        {
            BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
            try
            {
                for (String s = br.readLine(); s != null; s = br.readLine())
                {
                    s = s.trim();
                    // Try to load first non-empty, non-commented line.
                    if ((s.length() > 0) && (s.charAt(0) != '#'))
                    {
                        return (FrameworkFactory) Class.forName(s).newInstance();
                    }
                }
            }
            finally
            {
                if (br != null) br.close();
            }
        }

        throw new Exception("Could not find framework factory.");
    }

}