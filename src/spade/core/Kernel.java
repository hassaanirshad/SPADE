/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */
package spade.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import spade.client.QueryParameters;
import spade.filter.FinalCommitFilter;
import spade.utility.LogManager;
import trace.profiler.client.ProfileConfig;

/**
 * This mediates between vertex and edges coming from Reporters, applying Filters to them, before
 * committing them to Storage, applying Transformers when responding local queries, and using Sketches
 * to accelerate distributed query responses.
 */

public class Kernel {
	
	static{
		System.setProperty("java.util.logging.manager", spade.utility.LogManager.class.getName());
		/** /
		try {
			PrintStream pStream = new PrintStream ("/tmp/logMgr.txt");

			pStream.println (System.getProperty ("java.util.logging.manager") + ": " +
							 LogManager.getLogManager ());
			pStream.flush ();
			pStream.close ();
		} catch (Exception e) {
			// do nothing
		}
		/**/
	}

    private static final String SPADE_ROOT = Settings.getProperty("spade_root");
    private static final String FILE_SEPARATOR = System.getProperty("file.separator");
    
    /**
     * Path to log files including the prefix.
     */
    private static final String PID_FILE = "spade.pid";

    /**
     * Path to configuration files.
     */
     private static final String CONFIG_PATH = SPADE_ROOT + FILE_SEPARATOR + "cfg";
    /**
     * Path to configuration file for storing state of SPADE instance (includes
     * currently added modules).
     */
    private static final String CONFIG_FILE = CONFIG_PATH + FILE_SEPARATOR + "spade.config";
    /**
     * Paths to key stores.
     */
    private static final String KEYSTORE_PATH = CONFIG_PATH + FILE_SEPARATOR + "ssl";
    private static final String SERVER_PUBLIC_PATH = KEYSTORE_PATH + FILE_SEPARATOR + "server.public";
    private static final String SERVER_PRIVATE_PATH = KEYSTORE_PATH + FILE_SEPARATOR + "server.private";
    private static final String CLIENT_PUBLIC_PATH = KEYSTORE_PATH + FILE_SEPARATOR + "client.public";
    private static final String CLIENT_PRIVATE_PATH = KEYSTORE_PATH + FILE_SEPARATOR + "client.private";
    /**
     * Path to log files.
     */
    private static final String LOG_PATH = SPADE_ROOT + FILE_SEPARATOR + "log";
    /**
     * Path to log files including the prefix.
     */
    private static final String LOG_PREFIX = "SPADE_";
    /**
     * Date/time suffix pattern for log files.
     */
    private static final String LOG_START_TIME_PATTERN = "MM.dd.yyyy-H.mm.ss";
    /**
     * Set of reporters active on the local SPADE instance.
     */
    private static Set<AbstractReporter> reporters;
    /**
     * Set of storages active on the local SPADE instance.
     */
    public static Set<AbstractStorage> storages;
    /**
     * Set of filters active on the local SPADE instance.
     */
    private static List<AbstractFilter> filters;
    /**
     * Set of transformers active on the local SPADE instance.
     */
    public static List<AbstractTransformer> transformers;
    /**
     * Set of sketches active on the local SPADE instance.
     */
    public static Set<AbstractSketch> sketches;
    /**
     * A map used to cache the remote sketches.
     */
    public static Map<String, AbstractSketch> remoteSketches;

    /**
     * Used to initiate shutdown. It is used by various threads to 
     * determine whether to continue running.
     */
    public static volatile boolean shutdown;
    /**
     * Used to indicate whether the transactions need to be flushed by
     * the storages.
     */
    public static volatile boolean flushTransactions;
    private static Thread mainThread;
    private static List<ServerSocket> serverSockets;
    private static Set<AbstractReporter> removereporters;
    private static Set<AbstractStorage> removestorages;
    private static final int BATCH_BUFFER_ELEMENTS = 1000000;
    private static final int MAIN_THREAD_SLEEP_DELAY = 10;
    private static final int REMOVE_WAIT_DELAY = 100;
    //private static final int FIRST_TRANSFORMER = 0;
    private static final int FIRST_FILTER = 0;
    // Strings for control client
    private static final String ADD_REPORTER_STORAGE_STRING = "add reporter|storage <class name> <arguments>";
    private static final String ADD_FILTER_TRANSFORMER_STRING = "add filter|transformer <class name> position=<number> <arguments>";
    private static final String ADD_SKETCH_STRING = "add sketch <class name>";
    private static final String REMOVE_REPORTER_STORAGE_SKETCH_STRING = "remove reporter|storage|sketch <class name>";
    private static final String REMOVE_FILTER_TRANSFORMER_STRING = "remove filter|transformer <position number>";
    private static final String LIST_STRING = "list reporters|storages|filters|sketches|transformers|all";
    private static final String CONFIG_STRING = "config load|save <filename>";
    private static final String EXIT_STRING = "exit";
    // Strings for query client
    private static final String QUERY_VERTEX_STRING = "<result> = getVertices(expression)";
    private static final String QUERY_EDGE1_STRING = "<result> = getEdges(source vertex id, destination vertex id)";
    private static final String QUERY_PATHS_STRING = "<result> = getPaths(source vertex id, destination vertex id, maximum length)";
    private static final String QUERY_LINEAGE_STRING = "<result> = getLineage(vertex id|<result>, depth, direction[, terminating expression])";
    private static final String QUERY_CHILDREN_STRING = "<result> = <result>.getChildren(expression)";
    private static final String QUERY_PARENTS_STRING = "<result> = <result>.getParents(expression)";
    private static final String QUERY_PRINT_STRING = "<result>.print(annotations)";
    private static final String QUERY_EXPORT_STRING = "export <result> <path>";
    private static final String QUERY_LIST_STRING = "list";
    private static final String QUERY_SELECT_STORAGE = "storage Neo4j|SQL (default: Neo4j)";    
    private static final String QUERY_EXIT_STRING = "exit";
    private static final Logger logger = Logger.getLogger(Kernel.class.getName());
    private static boolean ANDROID_PLATFORM = false;
    // Members for creating secure sockets
    private static KeyStore clientKeyStorePublic;
    private static KeyStore clientKeyStorePrivate;
    private static KeyStore serverKeyStorePublic;
    private static KeyStore serverKeyStorePrivate;
    public static SSLSocketFactory sslSocketFactory;
    public static SSLServerSocketFactory sslServerSocketFactory;
    
    private final static int CONTROL_CLIENT_READ_TIMEOUT = 1000; //time to timeout after when reading from the control client socket
    
    public static ProfileConfig profileConfig = null;
        
    private static void setupKeyStores() throws Exception {

        serverKeyStorePublic = KeyStore.getInstance("JKS");
        serverKeyStorePublic.load(new FileInputStream(SERVER_PUBLIC_PATH), "public".toCharArray());
        serverKeyStorePrivate = KeyStore.getInstance("JKS");
        serverKeyStorePrivate.load(new FileInputStream(SERVER_PRIVATE_PATH), "private".toCharArray());
        clientKeyStorePublic = KeyStore.getInstance("JKS");
        clientKeyStorePublic.load(new FileInputStream(CLIENT_PUBLIC_PATH), "public".toCharArray());
        clientKeyStorePrivate = KeyStore.getInstance("JKS");
        clientKeyStorePrivate.load(new FileInputStream(CLIENT_PRIVATE_PATH), "private".toCharArray());
    }

    private static void setupClientSSLContext() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextInt();

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(serverKeyStorePublic);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(clientKeyStorePrivate, "private".toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), secureRandom);
        sslSocketFactory = sslContext.getSocketFactory();
    }

    private static void setupServerSSLContext() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextInt();

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(clientKeyStorePublic);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(serverKeyStorePrivate, "private".toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), secureRandom);
        sslServerSocketFactory = sslContext.getServerSocketFactory();
    }

	protected static FileHandler newFileHandler (final String logFilename) throws IOException
	{
		if (System.getProperty ("spade.utility.LogManager.flush") != null)
			return new FileHandler (logFilename) {
				@Override
				public synchronized void publish (final LogRecord record) {
					super.publish (record);
					flush ();
				};
			};
		else
            return new FileHandler (logFilename);
	}

    /**
     * The main initialization function.
     *
     * @param args
     */
    public static void main(String args[]) {
        if (args.length == 1 && args[0].equals("android")) {
            ANDROID_PLATFORM = true;
        }

    	try{
    		profileConfig = ProfileConfig.createInstance("cfg/spade.trace.profiler.ProfileConfig.config");
    	}catch(Exception e){
    		Logger.getLogger("ProfileConfigSetupLogger").log(Level.SEVERE, "Error in init of profile config", e);
    	}

        // Set up context for secure connections
        if (!ANDROID_PLATFORM) {
            try {
                setupKeyStores();
                setupClientSSLContext();
                setupServerSSLContext();
            } catch (Exception exception) {
                logger.log(Level.SEVERE, null, exception);
            }
        }

        try {
            // Configure the global exception logger

            String logFilename = System.getProperty("spade.log");
            if(logFilename == null){
                new File(LOG_PATH).mkdirs();
                Date currentTime = new java.util.Date(System.currentTimeMillis());
                String logStartTime = new java.text.SimpleDateFormat(LOG_START_TIME_PATTERN).format(currentTime);
                logFilename = LOG_PATH + FILE_SEPARATOR + LOG_PREFIX + logStartTime + ".log";
            }
            final Handler logFileHandler = newFileHandler(logFilename);
            logFileHandler.setFormatter(new SimpleFormatter());
            Logger.getLogger("").addHandler(logFileHandler);
            
        } catch (IOException | SecurityException exception) {
            System.err.println("Error initializing exception logger");
        }

        // Register a shutdown hook to terminate gracefully
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (!shutdown) {
                    shutdown = true;
                    shutdown();
                }
            }
        });

        // Basic initialization
        reporters = Collections.synchronizedSet(new HashSet<AbstractReporter>());
        storages = Collections.synchronizedSet(new HashSet<AbstractStorage>());
        removereporters = Collections.synchronizedSet(new HashSet<AbstractReporter>());
        removestorages = Collections.synchronizedSet(new HashSet<AbstractStorage>());
        transformers = Collections.synchronizedList(new LinkedList<AbstractTransformer>());
        filters = Collections.synchronizedList(new LinkedList<AbstractFilter>());
        sketches = Collections.synchronizedSet(new HashSet<AbstractSketch>());
        remoteSketches = Collections.synchronizedMap(new HashMap<String, AbstractSketch>());
        serverSockets = Collections.synchronizedList(new LinkedList<ServerSocket>());

        shutdown = false;
        flushTransactions = true;

        // Initialize the SketchManager and the final commit filter.
        // The FinalCommitFilter acts as a terminator for the filter list
        // and also maintains a pointer to the list of active storages to which
        // the provenance data is finally passed. It also has a reference to
        // the SketchManager and triggers its putVertex() and putEdge() methods
        FinalCommitFilter commitFilter = new FinalCommitFilter();
        commitFilter.storages = storages;
        commitFilter.sketches = sketches;
        filters.add(commitFilter);

        // The final transformer is used to send vertex and edge objects to
        // their corresponding result Graph.
        // FinalTransformer finalTransformer = new FinalTransformer();
        // transformers.add(finalTransformer);

        // Initialize the main thread. This thread performs critical
        // provenance-related
        // work inside SPADE. It extracts provenance objects (vertices, edges)
        // from the
        // buffers, adds the source_reporter annotation to each object which is
        // class name
        // of the reporter, and then sends these objects to the filter list.
        // This thread is also used for cleanly removing reporters and storages
        // (through
        // the control commands and also when shutting down). This is done by
        // ensuring that
        // once a reporter is marked for removal, the provenance objects from
        // its buffer are
        // completely flushed.
        Runnable mainRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        if (flushTransactions) {
                            // Flushing of transactions is also handled by this
                            // thread to ensure that
                            // there are no errors/problems when using storages
                            // that are sensitive to
                            // thread-context for their transactions. For
                            // example, this is true for
                            // the embedded neo4j graph database.
                            for (AbstractStorage currentStorage : storages) {
                                currentStorage.flushTransactions();
                            }
                            flushTransactions = false;
                        }
                        if (!removestorages.isEmpty()) {
                            // Check if a storage is marked for removal. If it
                            // is, shut it down and
                            // remove it from the list.
                            for (Iterator<AbstractStorage> iterator = removestorages.iterator(); iterator.hasNext();) {
                                AbstractStorage currentStorage = iterator.next();
                                currentStorage.shutdown();
                                iterator.remove();
                            }
                        }
                        for (AbstractReporter reporter : reporters) {
                            // This loop performs the actual task of committing
                            // provenance data to
                            // the storages. Each reporter is selected and the
                            // nested loop is used to
                            // extract buffer elements in a batch manner for
                            // increased efficiency.
                            // The elements are then passed to the filter list.
                            Buffer buffer = reporter.getBuffer();
                            for (int i = 0; i < BATCH_BUFFER_ELEMENTS; i++) {
                                Object bufferelement = buffer.getBufferElement();
                                if (bufferelement instanceof AbstractVertex) {
                                    AbstractVertex tempVertex = (AbstractVertex) bufferelement;
                                    filters.get(FIRST_FILTER).putVertex(tempVertex);
                                } else if (bufferelement instanceof AbstractEdge) {
                                    AbstractEdge tempEdge = (AbstractEdge) bufferelement;
                                    filters.get(FIRST_FILTER).putEdge(tempEdge);
                                } else if (bufferelement == null) {
                                    if (removereporters.contains(reporter)) {
                                        removereporters.remove(reporter);
                                    }
                                    break;
                                }
                            }
                        }
                        Thread.sleep(MAIN_THREAD_SLEEP_DELAY);
                    }
                } catch (Exception exception) {
                    logger.log(Level.SEVERE, null, exception);
                }
            }
        };
        mainThread = new Thread(mainRunnable, "mainSPADE-Thread");
        mainThread.start();

        // This thread creates the input and output pipes used for control (and
        // also used
        // by the control client). The exit value is used to determine if the
        // pipes were
        // successfully created. The input pipe (to which commands are issued)
        // is read in
        // a loop and the commands are processed.
        Runnable controlRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    ServerSocket serverSocket = null;
                    int port = Integer.parseInt(Settings.getProperty("local_control_port"));
                    if (ANDROID_PLATFORM) {
                        serverSocket = new ServerSocket(port);
                    } else {
                        serverSocket = sslServerSocketFactory.createServerSocket(port);
                        ((SSLServerSocket) serverSocket).setNeedClientAuth(true);
                    }
                    serverSockets.add(serverSocket);
                    while (!shutdown) {
                        Socket controlSocket = serverSocket.accept();
                        LocalControlConnection thisConnection = new LocalControlConnection(controlSocket);
                        Thread connectionThread = new Thread(thisConnection);
                        connectionThread.start();
                    }
                } catch (SocketException exception) {
                    // Do nothing... this is triggered on shutdown.
                } catch (NumberFormatException | IOException exception) {
                    logger.log(Level.SEVERE, null, exception);
                }
            }
        };
        Thread controlThread = new Thread(controlRunnable, "controlSocket-Thread");
        controlThread.start();

        Runnable queryRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    ServerSocket serverSocket = null;
                    int port = Integer.parseInt(Settings.getProperty("local_query_port"));
                    if (ANDROID_PLATFORM) {
                        serverSocket = new ServerSocket(port);
                    } else {
                        serverSocket = sslServerSocketFactory.createServerSocket(port);
                        ((SSLServerSocket) serverSocket).setNeedClientAuth(true);
                    }
                    serverSockets.add(serverSocket);
                    while (!shutdown) {
                        Socket querySocket = serverSocket.accept();
                        LocalQueryConnection thisConnection = new LocalQueryConnection(querySocket);
                        Thread connectionThread = new Thread(thisConnection);
                        connectionThread.start();
                    }
                } catch (SocketException exception) {
                    // Do nothing... this is triggered on shutdown.
                } catch (NumberFormatException | IOException exception) {
                    logger.log(Level.SEVERE, null, exception);
                }
            }
        };
        Thread queryThread = new Thread(queryRunnable, "querySocket-Thread");
        queryThread.start();

        // This thread creates a server socket for remote querying. When a query
        // connection
        // is established, another new thread is created for that connection
        // object. The
        // remote query server is therefore a multithreaded server.
        Runnable remoteRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    ServerSocket serverSocket = null;
                    int port = Integer.parseInt(Settings.getProperty("remote_query_port"));
                    if (ANDROID_PLATFORM) {
                        serverSocket = new ServerSocket(port);
                    } else {
                        serverSocket = sslServerSocketFactory.createServerSocket(port);
                        ((SSLServerSocket) serverSocket).setNeedClientAuth(true);
                    }
                    serverSockets.add(serverSocket);
                    while (!shutdown) {
                        Socket clientSocket = serverSocket.accept();
                        QueryConnection thisConnection = new QueryConnection(clientSocket);
                        Thread connectionThread = new Thread(thisConnection);
                        connectionThread.start();
                    }
                } catch (SocketException exception) {
                    // Do nothing... this is triggered on shutdown.
                } catch (NumberFormatException | IOException exception) {
                    logger.log(Level.SEVERE, null, exception);
                }
            }
        };
        Thread remoteThread = new Thread(remoteRunnable, "remoteQuery-Thread");
        remoteThread.start();

        // This thread creates a server socket for remote sketches. When a
        // sketch connection
        // is established, another new thread is created for that connection
        // object. The
        // remote sketch server is therefore a multithreaded server.
        Runnable sketchRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    ServerSocket serverSocket = null;
                    int port = Integer.parseInt(Settings.getProperty("remote_sketch_port"));
                    if (ANDROID_PLATFORM) {
                        serverSocket = new ServerSocket(port);
                    } else {
                        serverSocket = sslServerSocketFactory.createServerSocket(port);
                        ((SSLServerSocket) serverSocket).setNeedClientAuth(true);
                    }
                    serverSockets.add(serverSocket);
                    while (!shutdown) {
                        Socket clientSocket = serverSocket.accept();
                        SketchConnection thisConnection = new SketchConnection(clientSocket);
                        Thread connectionThread = new Thread(thisConnection);
                        connectionThread.start();
                    }
                } catch (SocketException exception) {
                    // Do nothing... this is triggered on shutdown.
                } catch (NumberFormatException | IOException exception) {
                    logger.log(Level.SEVERE, null, exception);
                }
            }
        };
        Thread sketchThread = new Thread(sketchRunnable, "remoteSketch-Thread");
        sketchThread.start();

        // Load the SPADE configuration from the default config file.
        configCommand("config load " + CONFIG_FILE, NullStream.out);
    }

    // The following two methods are called by the Graph object when adding
    // vertices
    // and edges to the result graph. Transformers are technically the same as
    // filters
    // and are used to modify/transform data as it is entered into a Graph
    // object.
    /**
     * Method called by a Graph object to send vertices to transformers before
     * they are finally added to the result.
     *
     * @param vertex The vertex to be transformed.
     */
//    public static void sendToTransformers(AbstractVertex vertex) {
//        ((AbstractFilter) transformers.get(FIRST_TRANSFORMER)).putVertex(vertex);
//    }

    /**
     * Method called by a Graph object to send edges to transformers before they
     * are finally added to the result.
     *
     * @param edge The edge to be transformed.
     */
//    public static void sendToTransformers(AbstractEdge edge) {
//        ((AbstractFilter) transformers.get(FIRST_TRANSFORMER)).putEdge(edge);
//    }

    /**
     * All command strings are passed to this function which subsequently calls
     * the correct method based on the command. Each command is determined by
     * the first token in the string.
     *
     * @param line The command string.
     * @param outputStream The output stream on which to print the result or any
     * output.
     */
    public static void executeCommand(String line, PrintStream outputStream) {
        if (line.startsWith("add")) {
            addCommand(line, outputStream);
        } else if (line.startsWith("list")) {
            listCommand(line, outputStream);
        } else if (line.startsWith("remove")) {
            removeCommand(line, outputStream);
        } else if (line.startsWith("config")) {
            configCommand(line, outputStream);
        } else {
            outputStream.println(getControlCommands());
        }
    }

    /**
     * The configCommand is used to load or save the current SPADE configuration
     * from/to a file.
     *
     * @param line The configuration command to execute.
     * @param outputStream The output stream on which to print the result or any
     * output.
     */
    public static void configCommand(String line, PrintStream outputStream) {
        String[] tokens = line.split("\\s+");
        if (tokens.length < 3) {
            outputStream.println("Usage:");
            outputStream.println("\t" + CONFIG_STRING);
            return;
        }
        // Determine whether the command is a load or a save.
        if (tokens[1].equalsIgnoreCase("load")) {
            outputStream.print("Loading configuration... ");
            BufferedReader configReader = null;
            try {
                configReader = new BufferedReader(new FileReader(tokens[2]));
                String configLine;
                while ((configLine = configReader.readLine()) != null) {
                    executeCommand(configLine, outputStream);
                }
            } catch (Exception exception) {
                outputStream.println("error! Unable to open configuration file for reading");
                logger.log(Level.SEVERE, null, exception);
                return;
            }finally{
            	try{
            		if(configReader != null){
            			configReader.close();
            		}
            	}catch(Exception e){
            		logger.log(Level.SEVERE, "Failed to close config reader");
            	}
            }
            outputStream.println("done");
        } else if (tokens[1].equalsIgnoreCase("save")) {
            // If the command is save, then write the current configuration.
            outputStream.print("Saving configuration... ");
            try {
                FileWriter configWriter = new FileWriter(tokens[2], false);
                for (int i = 0; i < filters.size() - 1; i++) {
                    String arguments = filters.get(i).arguments;
                    configWriter.write("add filter " + filters.get(i).getClass().getName().split("\\.")[2] + " " + i);
                    if (arguments != null) {
                        configWriter.write(" " + arguments);
                    }
                    configWriter.write("\n");
                }
                for (AbstractSketch sketch : sketches) {
                    configWriter.write("sketch " + sketch.getClass().getName().split("\\.")[2] + "\n");
                }
                for (AbstractStorage storage : storages) {
                    String arguments = storage.arguments;
                    configWriter.write("add storage " + storage.getClass().getName().split("\\.")[2]);
                    if (arguments != null) {
                        configWriter.write(" " + arguments);
                    }
                    configWriter.write("\n");
                }
                for (AbstractReporter reporter : reporters) {
                    String arguments = reporter.arguments;
                    configWriter.write("add reporter " + reporter.getClass().getName().split("\\.")[2]);
                    if (arguments != null) {
                        configWriter.write(" " + arguments);
                    }
                    configWriter.write("\n");
                }
                synchronized (transformers) {
                	for(int i = 0; i<transformers.size(); i++){
                		String arguments = transformers.get(i).arguments;
                		configWriter.write("add transformer " + transformers.get(i).getClass().getName().split("\\.")[2] + " " + (i + 1));
                		if(arguments != null){
                			configWriter.write(" " + arguments);
                		}
                		configWriter.write("\n");
                	}
				}
                configWriter.close();
            } catch (Exception exception) {
                outputStream.println("error! Unable to open configuration file for writing");
                logger.log(Level.SEVERE, null, exception);
                return;
            }
            outputStream.println("done");
        } else {
            outputStream.println("Usage:");
            outputStream.println("\t" + CONFIG_STRING);
        }
    }

    /**
     * This method is triggered by the query client and calls the main query
     * method to retrieve the result before exporting it to the desired path.
     *
     * @param line The query expression.
     * @return
     */
    public static Graph queryCommand(String line) {
        Graph resultGraph = Query.executeQuery(line, false);
        return resultGraph;
    }

    /**
     * Method to display control commands to the output stream. The control and
     * query commands are displayed using separate methods since these commands
     * are issued from different clients.
     *
     * @return
     */
    public static String getControlCommands() {
        StringBuilder string = new StringBuilder();
        string.append("Available commands:\n");
        string.append("\t" + ADD_REPORTER_STORAGE_STRING + "\n");
        string.append("\t" + ADD_FILTER_TRANSFORMER_STRING + "\n");
        string.append("\t" + ADD_SKETCH_STRING + "\n");
        string.append("\t" + REMOVE_REPORTER_STORAGE_SKETCH_STRING + "\n");
        string.append("\t" + REMOVE_FILTER_TRANSFORMER_STRING + "\n");
        string.append("\t" + LIST_STRING + "\n");
        string.append("\t" + CONFIG_STRING + "\n");
        string.append("\t" + EXIT_STRING);
        return string.toString();
    }

    /**
     * Method to display query commands to the given output stream.
     *
     * @return
     */
    public static String getQueryCommands() {
        StringBuilder string = new StringBuilder();
        string.append("Available commands:\n");
        string.append("\t" + QUERY_VERTEX_STRING + "\n");
        string.append("\t" + QUERY_EDGE1_STRING + "\n");
        string.append("\t" + QUERY_PATHS_STRING + "\n");
        string.append("\t" + QUERY_LINEAGE_STRING + "\n");
        string.append("\t" + QUERY_CHILDREN_STRING + "\n");
        string.append("\t" + QUERY_PARENTS_STRING + "\n");
        string.append("\n");        
        string.append("\t" + QUERY_PRINT_STRING + "\n");
        string.append("\t" + QUERY_EXPORT_STRING + "\n");
        string.append("\t" + QUERY_LIST_STRING + "\n");
        // string.append("\t" + QUERY_SELECT_STORAGE + "\n");
        string.append("\t" + QUERY_EXIT_STRING);
        return string.toString();
    }
    
    private static SimpleEntry<String, String> getPositionAndArguments(String partOfCommand){
    	try{
	    	int indexOfPosition = partOfCommand.startsWith("position") ? 0 : partOfCommand.indexOf(" position")  < 0 ? -1 : partOfCommand.indexOf(" position") + 1;
	    	String positionSubstring = partOfCommand.substring(indexOfPosition);
	    	int indexOfEquals = positionSubstring.indexOf('=');
	    	String positionValue = "";
	    	int i = indexOfEquals + 1;
	    	for(; i < positionSubstring.length(); i++){
	    		if(positionValue.isEmpty()){
	    			if(positionSubstring.charAt(i) != ' '){
	    				positionValue += positionSubstring.charAt(i);
	    			}
	    		}else{ //not empty
	    			if(positionSubstring.charAt(i) != ' '){
	    				positionValue += positionSubstring.charAt(i);
	    			}else{
	    				break;
	    			}
	    		}
	    	}
	    	i = i < positionSubstring.length() ? i++ : i;
	    	String arguments = partOfCommand.replace(partOfCommand.substring(indexOfPosition, indexOfPosition+i), "");
	    	return new SimpleEntry<String, String>(positionValue, arguments);
		}catch(Exception e){
    		logger.log(Level.SEVERE, null, e);
    		return null;
    	}
    }

    /**
     * Method to add extensions.
     *
     * @param line The add command issued using the control client.
     * @param outputStream The output stream on which to print the results or
     * any output.
     */
    public static void addCommand(String line, PrintStream outputStream) {
        String[] tokens = line.split("\\s+", 4);
        if (tokens.length < 2) {
            outputStream.println("Usage:");
            outputStream.println("\t" + ADD_REPORTER_STORAGE_STRING);
            outputStream.println("\t" + ADD_FILTER_TRANSFORMER_STRING);
            outputStream.println("\t" + ADD_SKETCH_STRING);
            return;
        }
        if (tokens[1].equalsIgnoreCase("reporter")) {
            if (tokens.length < 3) {
                outputStream.println("Usage:");
                outputStream.println("\t" + ADD_REPORTER_STORAGE_STRING);
                return;
            }
            String classname = tokens[2];
            String arguments = (tokens.length == 3) ? null : tokens[3];
            // Get the reporter by classname and create a new instance.
            logger.log(Level.INFO, "Adding reporter: {0}", classname);
            outputStream.print("Adding reporter " + classname + "... ");
            AbstractReporter reporter;
            try {
                reporter = (AbstractReporter) Class.forName("spade.reporter." + classname).newInstance();
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
                outputStream.println("error: Unable to find/load class");
                logger.log(Level.SEVERE, null, ex);
                return;
            }
            // Create a new buffer and allocate it to this reporter.
            Buffer buffer = new Buffer();
            reporter.setBuffer(buffer);
            if (reporter.launch(arguments)) {
                // The launch() method must return true to indicate a successful
                // launch.
                // On true, the reporter is added to the reporters set and the
                // buffer
                // is put into a HashMap keyed by the reporter (this is used by
                // the main
                // SPADE thread to extract buffer elements).
                reporter.arguments = arguments;
                reporters.add(reporter);
                logger.log(Level.INFO, "Reporter added: {0}", classname);
                outputStream.println("done");
            } else {
                outputStream.println("failed");
            }
        } else if (tokens[1].equalsIgnoreCase("storage")) {
            if (tokens.length < 3) {
                outputStream.println("Usage:");
                outputStream.println("\t" + ADD_REPORTER_STORAGE_STRING);
                return;
            }
            String classname = tokens[2];
            String arguments = (tokens.length == 3) ? null : tokens[3];
            // Get the storage by classname and create a new instance.
            logger.log(Level.INFO, "Adding storage: {0}", classname);
            outputStream.print("Adding storage " + classname + "... ");
            AbstractStorage storage;
            try {
                storage = (AbstractStorage) Class.forName("spade.storage." + classname).newInstance();
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
                outputStream.println("error: Unable to find/load class");
                logger.log(Level.SEVERE, null, ex);
                return;
            }
            if (storage.initialize(arguments)) {
                // The initialize() method must return true to indicate
                // successful startup.
                // On true, the storage is added to the storages set.
                storage.arguments = arguments;
                storage.vertexCount = 0;
                storage.edgeCount = 0;
                storages.add(storage);
                logger.log(Level.INFO, "Storage added: {0}", classname);
                outputStream.println("done");
            } else {
                outputStream.println("failed");
            }
        } else if (tokens[1].equalsIgnoreCase("filter")) {
            if (tokens.length < 4) {
                outputStream.println("Usage:");
                outputStream.println("\t" + ADD_FILTER_TRANSFORMER_STRING);
                return;
            }
            String classname = tokens[2];
            SimpleEntry<String, String> positionArgumentsEntry = getPositionAndArguments(tokens[3]);
            String position = null, arguments = null;
            if(positionArgumentsEntry != null){
            	position = positionArgumentsEntry.getKey();
            	arguments = positionArgumentsEntry.getValue();
            }
            logger.log(Level.INFO, "Adding filter: {0}", classname);
            outputStream.print("Adding filter " + classname + "... ");
            int index;
            try {
                index = Integer.parseInt(position) - 1;
            } catch (NumberFormatException numberFormatException) {
                outputStream.println("error: Position must be specified and must be a number");
                return;
            }
            // Get the filter by classname and create a new instance.
            AbstractFilter filter;
            try {
                filter = (AbstractFilter) Class.forName("spade.filter." + classname).newInstance();
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
                outputStream.println("error: Unable to find/load class");
                logger.log(Level.SEVERE, null, ex);
                return;
            }
            // Initialize filter if arguments are provided
            filter.initialize(arguments);
            filter.arguments = arguments;
            // The argument is the index at which the filter is to be inserted.
            if (index >= filters.size()) {
                outputStream.println("error: Invalid position");
                return;
            }
            // Set the next filter of this newly added filter.
            filter.setNextFilter((AbstractFilter) filters.get(index));
            if (index > 0) {
                // If the newly added filter is not the first in the list, then
                // then configure the previous filter in the list to point to
                // this
                // newly added filter as its next.
                ((AbstractFilter) filters.get(index - 1)).setNextFilter(filter);
            }
            // Add filter to the list.
            filters.add(index, filter);
            logger.log(Level.INFO, "Filter added: {0}", classname);
            outputStream.println("done");
        } else if (tokens[1].equalsIgnoreCase("transformer")) {
            if (tokens.length < 4) {
                outputStream.println("Usage:");
                outputStream.println("\t" + ADD_FILTER_TRANSFORMER_STRING);
                return;
            }
            String classname = tokens[2];
            SimpleEntry<String, String> positionArgumentsEntry = getPositionAndArguments(tokens[3]);
            String position = null, arguments = null;
            if(positionArgumentsEntry != null){
            	position = positionArgumentsEntry.getKey();
            	arguments = positionArgumentsEntry.getValue();
            }
            logger.log(Level.INFO, "Adding transformer: {0}", classname);
            outputStream.print("Adding transformer " + classname + "... ");
            int index;
            try {
                index = Integer.parseInt(position) - 1;
            } catch (NumberFormatException numberFormatException) {
            	outputStream.println("error: Position must be specified and must be a number");
                return;
            }
            // Get the transformer by classname and create a new instance.
            AbstractTransformer transformer;
            try {
            	transformer = (AbstractTransformer) Class.forName("spade.transformer." + classname).newInstance();
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
                outputStream.println("error: Unable to find/load class");
                logger.log(Level.SEVERE, null, ex);
                return;
            }
            // Initialize filter if arguments are provided
            if(transformer.initialize(arguments)){
	            transformer.arguments = arguments;
	            // The argument is the index at which the transformer is to be
	            // inserted.
	            if (index > transformers.size() || index < 0) {
	                outputStream.println("error: Invalid position");
	                return;
	            }
	           
	            synchronized (transformers) {
					transformers.add(index, transformer);
				}
	            
	            logger.log(Level.INFO, "Transformer added: {0}", classname);
	            outputStream.println("done");
            }else{
            	outputStream.println("failed");
            }
        } else if (tokens[1].equalsIgnoreCase("sketch")) {
            if (tokens.length < 3) {
                outputStream.println("Usage:");
                outputStream.println("\t" + ADD_SKETCH_STRING);
                return;
            }
            String classname = tokens[2];
            // Get the sketch by classname and create a new instance.
            logger.log(Level.INFO, "Adding sketch: {0}", classname);
            outputStream.print("Adding sketch " + classname + "... ");
            AbstractSketch sketch;
            try {
                sketch = (AbstractSketch) Class.forName("spade.sketch." + classname).newInstance();
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
                outputStream.println("error: Unable to find/load class");
                logger.log(Level.SEVERE, null, ex);
                return;
            }
            sketches.add(sketch);
            logger.log(Level.INFO, "Sketch added: {0}", classname);
            outputStream.println("done");
        } else {
            outputStream.println("Usage:");
            outputStream.println("\t" + ADD_REPORTER_STORAGE_STRING);
            outputStream.println("\t" + ADD_FILTER_TRANSFORMER_STRING);
            outputStream.println("\t" + ADD_SKETCH_STRING);
        }
    }

    /**
     * Method to list extensions.
     *
     * @param line The list command issued using the control client.
     * @param outputStream The output stream on which to print the results or
     * any output.
     */
    public static void listCommand(String line, PrintStream outputStream) {
        String[] tokens = line.split("\\s+");
        String verbose_token = "";
        if (tokens.length < 2) {
            outputStream.println("Usage:");
            outputStream.println("\t" + LIST_STRING);
            return;
        }

        if (tokens[1].equalsIgnoreCase("reporters")) {
            if (reporters.isEmpty()) {
                // Nothing to list if the set of reporters is empty.
                outputStream.println("No reporters added");
                return;
            }
            outputStream.println(reporters.size() + " reporter(s) added:");
            int count = 1;
            for (AbstractReporter reporter : reporters) {
                // Print the names and arguments of all reporters.
                String arguments = reporter.arguments;
                outputStream.print("\t" + count + ". " + reporter.getClass().getName().split("\\.")[2]);
                if (arguments != null) {
                    outputStream.print(" (" + arguments + ")");
                }
                outputStream.println();
                count++;
            }
        } else if (tokens[1].equalsIgnoreCase("storages")) {
            if (storages.isEmpty()) {
                // Nothing to list if the set of storages is empty.
                outputStream.println("No storages added");
                return;
            }
            outputStream.println(storages.size() + " storage(s) added:");
            int count = 1;
            for (AbstractStorage storage : storages) {
                // Print the names and arguments of all storages.
                String arguments = storage.arguments;
                outputStream.print("\t" + count + ". " + storage.getClass().getName().split("\\.")[2]);
                if (arguments != null) {
                    outputStream.print(" (" + arguments + ")");
                }
                outputStream.println();
                count++;
            }
        } else if (tokens[1].equalsIgnoreCase("filters")) {
            if (filters.size() == 1) {
                // The size of the filters list will always be at least 1
                // because
                // of the FinalCommitFilter. The user is not made aware of the
                // presence of this filter and it is only used for committing
                // provenance data to the storages. Therefore, there is nothing
                // to list if the size of the filters list is 1.
                outputStream.println("No filters added");
                return;
            }
            outputStream.println((filters.size() - 1) + " filter(s) added:");
            for (int i = 0; i < filters.size() - 1; i++) {
                // Loop through the filters list, printing their names (except
                // for the last FinalCommitFilter).
                String arguments = filters.get(i).arguments;
                outputStream.print("\t" + (i + 1) + ". " + filters.get(i).getClass().getName().split("\\.")[2]);
                if (arguments != null) {
                    outputStream.print(" (" + arguments + ")");
                }
                outputStream.println();
            }
        } else if (tokens[1].equalsIgnoreCase("transformers")) {
            if (transformers.size() == 0) {
                outputStream.println("No transformers added");
                return;
            }
            outputStream.println((transformers.size()) + " transformer(s) added:");
            StringBuffer transformersListString = new StringBuffer();
            synchronized (transformers) {
            	for (int i = 0; i < transformers.size(); i++) {
                    // Loop through the transformers list, printing their names
                    // (except
                    // for the last FinalTransformer).
            		transformersListString.append("\t").append((i + 1)).append(". ").append(transformers.get(i).getClass().getName().split("\\.")[2]);
                    if (transformers.get(i).arguments != null) {
                        transformersListString.append(" (" + transformers.get(i).arguments + ")");
                    }
                    transformersListString.append("\n");
                }
			}
            outputStream.print(transformersListString);
        } else if (tokens[1].equalsIgnoreCase("sketches")) {
            if (sketches.isEmpty()) {
                // Nothing to list if the set of sketches is empty.
                outputStream.println("No sketches added");
                return;
            }
            outputStream.println(sketches.size() + " sketch(es) added:");
            int count = 1;
            for (AbstractSketch sketch : sketches) {
                // Print the names of all sketches.
                outputStream.println("\t" + count + ". " + sketch.getClass().getName().split("\\.")[2]);
                count++;
            }
        } else if (tokens[1].equalsIgnoreCase("all")) {
            listCommand("list reporters " + verbose_token, outputStream);
            listCommand("list storages " + verbose_token, outputStream);
            listCommand("list filters " + verbose_token, outputStream);
            listCommand("list transformers " + verbose_token, outputStream);
            listCommand("list sketches " + verbose_token, outputStream);
        } else {
            outputStream.println("Usage:");
            outputStream.println("\t" + LIST_STRING);
        }
    }

    /**
     * Method to remove extensions.
     *
     * @param line The remove command issued using the control client.
     * @param outputStream The output stream on which to print the results or
     * any output.
     */
    public static void removeCommand(String line, PrintStream outputStream) {
        String[] tokens = line.split("\\s+");
        if (tokens.length < 3) {
            outputStream.println("Usage:");
            outputStream.println("\t" + REMOVE_REPORTER_STORAGE_SKETCH_STRING);
            outputStream.println("\t" + REMOVE_FILTER_TRANSFORMER_STRING);
            return;
        }
        try {
            if (tokens[1].equalsIgnoreCase("reporter")) {
                boolean found = false;
                for (Iterator<AbstractReporter> reporterIterator = reporters.iterator(); reporterIterator.hasNext();) {
                    AbstractReporter reporter = reporterIterator.next();
                    // Search for the given reporter in the set of reporters.
                    if (reporter.getClass().getName().equals("spade.reporter." + tokens[2])) {
                        // Mark the reporter for removal by adding it to the
                        // removereporters set.
                        // This will enable the main SPADE thread to cleanly
                        // flush the reporter
                        // buffer and remove it.
                        reporter.shutdown();
                        removereporters.add(reporter);
                        found = true;
                        logger.log(Level.INFO, "Shutting down reporter: {0}", tokens[2]);
                        outputStream.print("Shutting down reporter " + tokens[2] + "... ");
                        while (removereporters.contains(reporter)) {
                            // Wait for other thread to safely remove reporter
                            Thread.sleep(REMOVE_WAIT_DELAY);
                        }
                        reporterIterator.remove();
                        logger.log(Level.INFO, "Reporter shut down: {0}", tokens[2]);
                        outputStream.println("done");
                        break;
                    }
                }
                if (!found) {
                    logger.log(Level.WARNING, "Reporter not found (for shutting down): {0}", tokens[2]);
                    outputStream.println("Reporter " + tokens[2] + " not found");
                }
            } else if (tokens[1].equalsIgnoreCase("storage")) {
                boolean found = false;
                for (Iterator<AbstractStorage> storageIterator = storages.iterator(); storageIterator.hasNext();) {
                    AbstractStorage storage = storageIterator.next();
                    // Search for the given storage in the storages set.
                    if (storage.getClass().getName().equals("spade.storage." + tokens[2])) {
                        // Mark the storage for removal by adding it to the
                        // removestorages set.
                        // This will enable the main SPADE thread to safely
                        // commit any transactions
                        // and then remove the storage.
                        long vertexCount = storage.vertexCount;
                        long edgeCount = storage.edgeCount;
                        removestorages.add(storage);
                        found = true;
                        logger.log(Level.INFO, "Shutting down storage: {0}", tokens[2]);
                        outputStream.print("Shutting down storage " + tokens[2] + "... ");
                        while (removestorages.contains(storage)) {
                            // Wait for other thread to safely remove storage
                            Thread.sleep(REMOVE_WAIT_DELAY);
                        }
                        storageIterator.remove();
                        logger.log(Level.INFO, "Storage shut down: {0} ({1} vertices and {2} edges were added)", new Object[]{tokens[2], vertexCount, edgeCount});
                        outputStream.println("done (" + vertexCount + " vertices and " + edgeCount + " edges added)");
                        break;
                    }
                }
                if (!found) {
                    logger.log(Level.WARNING, "Storage not found (for shutting down): {0}", tokens[2]);
                    outputStream.println("Storage " + tokens[2] + " not found");
                }
            } else if (tokens[1].equalsIgnoreCase("filter")) {
                // Filter removal is done by the index number (beginning from
                // 1).
                int index = Integer.parseInt(tokens[2]);
                if ((index <= 0) || (index >= filters.size())) {
                    logger.log(Level.WARNING, "Error: Unable to remove filter - bad index");
                    outputStream.println("Error: Unable to remove filter - bad index");
                    return;
                }
                String filterName = filters.get(index - 1).getClass().getName();
                logger.log(Level.INFO, "Removing filter {0}", filterName.split("\\.")[2]);
                outputStream.print("Removing filter " + filterName.split("\\.")[2] + "... ");
                filters.get(index - 1).shutdown();
                if (index > 1) {
                    // Update the internal links between filters by calling the
                    // setNextFilter
                    // method on the filter just before the one being removed.
                    // The (index-1)
                    // check is used because this method is not to be called on
                    // the first filter.
                    ((AbstractFilter) filters.get(index - 2)).setNextFilter((AbstractFilter) filters.get(index));
                }
                filters.remove(index - 1);
                logger.log(Level.INFO, "Filter Removed: {0}", filterName.split("\\.")[2]);
                outputStream.println("done");
            } else if (tokens[1].equalsIgnoreCase("transformer")) {
            	int index;
            	try{
            		index = Integer.parseInt(tokens[2]) - 1;
            	}catch(Exception e){
            		logger.log(Level.WARNING, "Error: Invalid index (Not a number)");
                    outputStream.println("Error: Invalid index (Not a number)");
            		return;
            	}
                if ((index < 0) || (index >= transformers.size())) {
                    logger.log(Level.WARNING, "Error: Unable to remove transformer - bad index");
                    outputStream.println("Error: Unable to remove transformer - bad index");
                    return;
                }
                String transformerName = transformers.get(index).getClass().getName().split("\\.")[2];
                logger.log(Level.INFO, "Removing transformer {0}", transformerName);
                outputStream.print("Removing transformer " + transformerName + "... ");
                AbstractTransformer removed = null;
                synchronized (transformers) {
                	removed = transformers.remove(index);
				}
                if(removed != null){
                	removed.shutdown();
                }
                logger.log(Level.INFO, "Transformer removed: {0}", transformerName);
                outputStream.println("done");
            } else if (tokens[1].equalsIgnoreCase("sketch")) {
                boolean found = false;
                for (Iterator<AbstractSketch> sketchIterator = sketches.iterator(); sketchIterator.hasNext();) {
                    AbstractSketch sketch = sketchIterator.next();
                    // Search for the given sketch in the sketches set.
                    if (sketch.getClass().getName().equals("spade.sketch." + tokens[2])) {
                        found = true;
                        logger.log(Level.INFO, "Removing sketch {0}", tokens[2]);
                        outputStream.print("Removing sketch: " + tokens[2] + "... ");
                        sketchIterator.remove();
                        logger.log(Level.INFO, "Sketch removed: {0}", tokens[2]);
                        outputStream.println("done");
                        break;
                    }
                }
                if (!found) {
                    logger.log(Level.WARNING, "Sketch not found: {0}", tokens[2]);
                    outputStream.println("Sketch " + tokens[2] + " not found");
                }
            } else {
                outputStream.println("Usage:");
                outputStream.println("\t" + REMOVE_REPORTER_STORAGE_SKETCH_STRING);
                outputStream.println("\t" + REMOVE_FILTER_TRANSFORMER_STRING);
            }
        } catch (InterruptedException | NumberFormatException removeCommandException) {
            outputStream.println("Usage:");
            outputStream.println("\t" + REMOVE_REPORTER_STORAGE_SKETCH_STRING);
            outputStream.println("\t" + REMOVE_FILTER_TRANSFORMER_STRING);
            logger.log(Level.WARNING, null, removeCommandException);
        }
    }

    /**
     * Method to shut down SPADE completely.
     */
    public static void shutdown() {
        logger.log(Level.INFO, "Shutting down SPADE....");
        
        // Save current configuration.
        configCommand("config save " + CONFIG_FILE, NullStream.out);
        // Shut down all reporters.
        for (AbstractReporter reporter : reporters) {
            reporter.shutdown();
        }
        // Wait for main thread to consume all provenance data.
        while (!reporters.isEmpty()) {
            for (Iterator<AbstractReporter> reporterIterator = reporters.iterator(); reporterIterator.hasNext();) {
                AbstractReporter currentReporter = reporterIterator.next();
                Buffer currentBuffer = currentReporter.getBuffer();
                if (currentBuffer.isEmpty()) {
                    reporterIterator.remove();
                }
            }
            try {
                Thread.sleep(MAIN_THREAD_SLEEP_DELAY);
            } catch (InterruptedException ex) {
                logger.log(Level.WARNING, null, ex);
            }
        }
        
        // Shut down filters.
        for (int i = 0; i < filters.size() - 1; i++) {
            filters.get(i).shutdown();
        }
        // Shut down storages.
        for (AbstractStorage storage : storages) {
            storage.shutdown();
        }
        
        // Shut down server sockets.
        for (ServerSocket socket : serverSockets) {
            try {
                socket.close();
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
        logger.log(Level.INFO, "SPADE stopped.");
        
        try {
            
            Files.deleteIfExists(Paths.get(PID_FILE));
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Could not delete PID file.");
        }
        
        // Allow LogManager to complete its response to the shutdown
        LogManager.shutdownReset();
        
        profileConfig.shutdown();
    }
    
    public static String getPidFileName(){
        return PID_FILE;
    }
}

class FinalTransformer extends AbstractFilter {

    // This transformer is the last one in the list so any vertices or edges
    // received by it need to be passed to the correct graph.
    @Override
    public void putVertex(AbstractVertex incomingVertex) {
        // incomingVertex.resultGraph.commitVertex(incomingVertex);
    }

    @Override
    public void putEdge(AbstractEdge incomingEdge) {
        // incomingEdge.resultGraph.commitEdge(incomingEdge);
    }
}

final class NullStream {

    public final static PrintStream out = new PrintStream(new OutputStream() {
        @Override
        public void close() {
        }

        @Override
        public void flush() {
        }

        @Override
        public void write(byte[] b) {
        }

        @Override
        public void write(byte[] b, int off, int len) {
        }

        @Override
        public void write(int b) {
        }
    });
}

class LocalControlConnection implements Runnable {

	private final Logger logger = Logger.getLogger(LocalControlConnection.class.getName());
    Socket controlSocket;

    LocalControlConnection(Socket socket) {
        controlSocket = socket;
    }

    @Override
    public void run() {
        try {
            OutputStream outStream = controlSocket.getOutputStream();
            InputStream inStream = controlSocket.getInputStream();

            BufferedReader controlInputStream = new BufferedReader(new InputStreamReader(inStream));
            PrintStream controlOutputStream = new PrintStream(outStream);
            try {
                while (!Kernel.shutdown) {
                    // Commands read from the input stream and executed.
                	try{
	                    String line = controlInputStream.readLine();
	                    if (line == null || line.equalsIgnoreCase("exit")) {
	                        break;
	                    }
	                    
	                    Kernel.executeCommand(line, controlOutputStream);
	                    
                    	// An empty line is printed to let the client know that the command output is complete.
                    	controlOutputStream.println("");

                	}catch(SocketTimeoutException exception){
                		logger.log(Level.SEVERE, null, exception);
                	}
                }
            } catch (IOException e) {
            	logger.log(Level.SEVERE, null, e);
                // Connection broken?
            } finally {
                controlInputStream.close();
                controlOutputStream.close();

                inStream.close();
                outStream.close();
                if (controlSocket.isConnected()) {
                    controlSocket.close();
                }
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
}

class LocalQueryConnection implements Runnable {

    Socket querySocket;

    LocalQueryConnection(Socket socket) {
        querySocket = socket;
    }

    @Override
    public void run() {
        try {
            OutputStream outStream = querySocket.getOutputStream();
            InputStream inStream = querySocket.getInputStream();
            ObjectOutputStream queryOutputStream = new ObjectOutputStream(outStream);
            BufferedReader queryInputStream = new BufferedReader(new InputStreamReader(inStream));

            while (!Kernel.shutdown) {
                // Commands read from the input stream and executed.
                String line = queryInputStream.readLine();
                if (line.equalsIgnoreCase("exit")) {
                    break;
                } else {
                    Graph resultGraph = Query.executeQuery(line, false);
                    if(resultGraph != null){
                    	resultGraph = iterateTransformers(resultGraph, line);
                        queryOutputStream.writeObject("graph");
                        queryOutputStream.writeObject(resultGraph);
                	}else {
                        queryOutputStream.writeObject(Kernel.getQueryCommands());
                    }
                }
            }
            queryInputStream.close();
            queryOutputStream.close();

            inStream.close();
            outStream.close();
            querySocket.close();
        } catch (Exception ex) {
            Logger.getLogger(LocalQueryConnection.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public Graph iterateTransformers(Graph graph, String query){
		synchronized (Kernel.transformers) {
			QueryParameters digQueryParams = QueryParameters.parseQuery(query);
			for(int i = 0; i< Kernel.transformers.size(); i++){
				AbstractTransformer transformer = Kernel.transformers.get(i);
				if(graph != null){
					try{
						graph = transformer.putGraph(graph, digQueryParams);
						if(graph != null){
							graph.commitIndex(); //commit after every transformer to enable reading without error
						}
					}catch(Exception e){
						Logger.getLogger(getClass().getName()).log(Level.SEVERE, null, e);
					}
				}else{
					break;
				}
			}
		} 
		
		return graph;
	}
}
