package cz.cuni.mff.ufal.dspace.core;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.dspace.core.ConfigurationManager;
import static org.apache.commons.lang.StringUtils.isEmpty;

public class DumpConfig {

	/**
	 * run through dsrun
	 * @param args
	 */
	public static void main(String[] args) throws ParseException {
            CommandLineParser parser = new PosixParser();
            Options options = new Options();
            options.addOption("d", "dump", false, "Dump all configs");
            options.addOption("c", "check", false, "Dumps only those values that contain ${} or nothing");
            CommandLine line = parser.parse(options, args);
            if(line.hasOption('d')){
            	dump();
            }else if(line.hasOption('c')){
            	check();
            }

	}

	private static void check() {
		List<Properties> modules = getAllProperties();
        for(Properties prop : modules){
        	for(Entry<Object, Object> entry : prop.entrySet()){
        		String value = ((String)entry.getValue());
        		if(value.contains("${)") || isEmpty(value)){
        			System.out.println(String.format("%s=%s", entry.getKey(), entry.getValue()));
        		}
        	}
        }
	}

	private static List<Properties> getAllProperties(){
		Properties main = ConfigurationManager.getProperties();
        File[] moduleCfgs = new File(ConfigurationManager.getProperty("dspace.dir") +
                                File.separator + "config" +
                                File.separator + "modules").listFiles();
        List<Properties> modules = new ArrayList<Properties>(moduleCfgs.length + 1);
        modules.add(main);
        for(File moduleCfg : moduleCfgs){
        	modules.add(ConfigurationManager.getProperties(moduleCfg.getName().replace(".cfg", "")));
        }
        return modules;
	}
	private static void dump() {
		List<Properties> modules = getAllProperties();
        for(Properties prop : modules){
        	for(Entry<Object, Object> entry : prop.entrySet()){
        		System.out.println(String.format("%s=%s", entry.getKey(), entry.getValue()));
        	}
        }
	}

}
