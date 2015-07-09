/*

Copyright 2014 Health and Social Care Information Centre
 Solution Assurance <damian.murphy@hscic.gov.uk>

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package org.warlock.spine.connection;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import org.warlock.spine.logging.SpineToolsLogger;
/**
 * The interface to locally-cached SDS endpoint details. The cache is indexed in the first
 * instance by service/interaction (with the colon delimiters replaced by equals signs
 * to keep the names "filename safe" on both Linux/Unix and Windows-based file systems),
 * which gives a set of directories under the cache root given by the 
 * <code>org.warlock.spine.sds.cachedir</code> system property. Each target party key then
 * gets a file inside that directory, named after the party key. The file is an instance of
 * SdsTransmissionDetails serialised as JSON.
 * 
 * This structure was chosen because it yields easily-processed, text files that can be hand-
 * edited if required, and that will work with external "cache management" mechanisms. Also
 * (at least from the perspective of the C#/.Net flavour of the MHS, which was elaborated
 * before the Java one), JSON is easy to serialise and read due to the availability of an 
 * open source interface that uses reflection to figure out how to do it for an arbitrary 
 * class. The Java version is explicitly written to match the C# files and to make them
 * compatible with one another. However such compatibility is not guaranteed if the files
 * are hand-edited in place.
 * 
 * The cache implements an in-memory copy of the cached SdsTransmissionDetails objects,
 * backed by the JSON files on disk. This is read when the SdsCache instance is constructed. A 
 * cache refresh (including to load details that have been hand-edited in one or more
 * cache files) can therefore be accomplished by instantiating a new cache.
 * 
 * @author Damian Murphy <damian.murphy@hscic.gov.uk>
 */
public class SdsCache 
{

    private String cacheDir = null;
    private int refresh = 0;
    
    // Keyed on service+interaction
    //    
    private HashMap<String,ArrayList<SdsTransmissionDetails>> transmission = null;
    
    /**
     * Instantiate the cache.
     * 
     * @param d Directory on disk for the files
     * @param r Cache refresh period in hours, or zero for no automatic refresh
     */ 
    SdsCache(String d, int r)
    {
        cacheDir = d;
        File f = new File(d);
        if (!f.exists())
            f.mkdirs();
        refresh = r;
        transmission = new HashMap<>();
        load();
    }

    /**
     *  Save the given SdsTransmissionDetails instance in the cache directory, in JSON representation.
     */ 
    void cacheTransmissionDetail(SdsTransmissionDetails sds)
    {
        if (transmission.containsKey(sds.getSvcIA())) {
            ArrayList<SdsTransmissionDetails> tx = transmission.get(sds.getSvcIA());
            SdsTransmissionDetails toswap = null;
            for (SdsTransmissionDetails s : tx) {
                if (s.getAsids().containsAll(sds.getAsids())) {
                    toswap = s;
                    break;
                }
            }
            if (toswap == null)
                tx.add(sds);
            else {
                tx.remove(toswap);
                tx.add(sds);
            }                    
        } else {
          ArrayList<SdsTransmissionDetails> tx = new ArrayList<>();
          tx.add(sds);
          transmission.put(sds.getSvcIA(), tx);
        }
        writeDetails(sds);
    }
    
    /**
     * Write the SdsTransmissionDetails instance to the cache.
     * 
     * @param sds 
     */
    private void writeDetails(SdsTransmissionDetails sds)
    {
        // Write to cache
        // 1. See if we have a directory entry for the SvcIA
        // 2. Create one if necessary
        // 3. Make a file for the asid
        // 4. Serialise "sds" to JSON
        //
        File[] files = (new File(cacheDir)).listFiles();
        boolean exists = false;
        String dname = null;
        for (File f : files)
        {
            String s = f.getName().replace('=', ':');
            if (s.equalsIgnoreCase(sds.getSvcIA())) {
                exists = true;
                dname = f.getName();
                break;
            }
        }
        if (dname == null) {
            dname = sds.getSvcIA().replace(':', '=');
        }
        File sdsDirectory = new File(cacheDir, dname);
        if (!exists) {            
            sdsDirectory.mkdirs();
        }
        JsonObject j = makeJson(sds);        
        File pk = new File(sdsDirectory, sds.getPartyKey());       
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(pk);
        }
        catch (java.io.FileNotFoundException e) {
            SpineToolsLogger.getInstance().log("org.warlock.spine.connection.SdsCache.writeDetails", e);
        }
        try (JsonWriter jw = Json.createWriter(fos)) {
            jw.writeObject(j);
        }
    }
    
    /**
     * Makes a JSON object by serialising the SdsTransmissionDetails in the
     * same order as the JSON.Net does in the C# equivalent.
     * 
     * @param sds
     * @return 
     */
    private JsonObject makeJson(SdsTransmissionDetails sds)
    {
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add(SdsTransmissionDetails.NHSIDCODE, sds.getOrgCode());
        job.add(SdsTransmissionDetails.SVCIA, sds.getSvcIA());
        job.add(SdsTransmissionDetails.SVCNAME, sds.getService());
        job.add(SdsTransmissionDetails.INTERACTIONID, sds.getInteractionId());
        job.add(SdsTransmissionDetails.RETRYINTERVAL, sds.getRetryInterval());
        job.add(SdsTransmissionDetails.PERSISTDURATION, sds.getPersistDuration());
        job.add(SdsTransmissionDetails.RETRIES, sds.getRetries());
        job.add(SdsTransmissionDetails.ENDPOINT, sds.getUrl());
        if (!sds.getAsids().isEmpty()) {
            JsonArrayBuilder jab = Json.createArrayBuilder();
            for (String s : sds.getAsids()) {
                jab.add(s);
            }
            job.add(SdsTransmissionDetails.ASIDS, jab);
        }
        job.add(SdsTransmissionDetails.PARTYKEY, sds.getPartyKey());        
        job.add(SdsTransmissionDetails.CPAID, sds.getCPAid());
        job.add(SdsTransmissionDetails.SYNCREPLY, sds.getSyncReply());
        job.add(SdsTransmissionDetails.ISSYNCHRONOUS, sds.isSynchronous());
        job.add(SdsTransmissionDetails.DUPELIM, sds.getDuplicateElimination());        
        job.add(SdsTransmissionDetails.ACKREQ, sds.getAckRequested());
        job.add(SdsTransmissionDetails.SOAPACTOR, sds.getSoapActor());        
        return job.build();
    }
    
    /**
     * Part of the boot process. Loads the cache into memory.
     */
    private void load() 
    {        
        File[] interactions = (new File(cacheDir)).listFiles();
        for (File f : interactions) {
            loadInteraction(f);
        }
    }
    
    /**
     * Load an individual directory of cache files.
     * @param cdir 
     */
    private void loadInteraction(File cdir) 
    {
        String svcinteraction = cdir.getName();
        svcinteraction = svcinteraction.replace('=', ':');
        File[] files = cdir.listFiles();
        ArrayList<SdsTransmissionDetails> tx = new ArrayList<>();
        transmission.put(svcinteraction, tx);
        for (File f : files) 
        {
            String sdstransmission = null;
            try {
                FileInputStream fis = new FileInputStream(f);
                JsonReader jr = Json.createReader(fis);
                JsonObject js = (JsonObject)jr.read();
                SdsTransmissionDetails sds = readDetails(js);
                if (sds != null)
                    tx.add(sds);
            }
            catch (Exception e)
            {
                SpineToolsLogger.getInstance().log("org.warlock.spine.connection.SdsCache.loadInteraction", e);
            }
            
        }
    }
    
    /**
     * Load an individual cache file.
     * 
     * @param j
     * @return 
     */
    @SuppressWarnings("CallToThreadDumpStack")
    private SdsTransmissionDetails readDetails(JsonObject j)
    {
        SdsTransmissionDetails sds = new SdsTransmissionDetails();
        sds.setAckRequested(j.getString(SdsTransmissionDetails.ACKREQ));
        sds.setCPAid(j.getString(SdsTransmissionDetails.CPAID));
        sds.setDuplicateElimination(j.getString(SdsTransmissionDetails.DUPELIM));
        sds.setInteractionId(j.getString(SdsTransmissionDetails.INTERACTIONID));
        sds.setOrgCode(j.getString(SdsTransmissionDetails.NHSIDCODE));
        sds.setPartyKey(j.getString(SdsTransmissionDetails.PARTYKEY));
        sds.setService(j.getString(SdsTransmissionDetails.SVCNAME));
        sds.setSoapActor(j.getString(SdsTransmissionDetails.SOAPACTOR));
        sds.setSvcIA(j.getString(SdsTransmissionDetails.SVCIA));
        sds.setSyncReply(j.getString(SdsTransmissionDetails.SYNCREPLY));
        sds.setUrl(j.getString(SdsTransmissionDetails.ENDPOINT));
        JsonArray asids = j.getJsonArray(SdsTransmissionDetails.ASIDS);
        if (asids != null) {
            for (int i = 0; i < asids.size(); i++) {
                String s = asids.getString(i).toString();
                sds.addAsid(s);
            }        
        }
        try {
            sds.setRetries(j.getInt(SdsTransmissionDetails.RETRIES));
            sds.setRetryInterval(j.getInt(SdsTransmissionDetails.RETRYINTERVAL));
            sds.setPersistDuration(j.getInt(SdsTransmissionDetails.PERSISTDURATION));
        }
        catch (NullPointerException enull) {
            // Ignore - just means that the value isn't found
            if (System.getProperty("DEBUG") != null)
                enull.printStackTrace();
        }
        catch (ClassCastException ecc) {
            // Log, but it means that there is some bad data in the cache,
            // so maybe "flag for refresh" would be appropriate ?
            SpineToolsLogger.getInstance().log("org.warlock.spine.connection.SdsCache.readDetails", ecc);
        }
        return sds;
    }
    
    /**
     *  Retrieve a list of SdsTransmissionDetails matching the given parameters
     *  
     * @param svcint "SvcIA" value consisting of TMS service name and HL7 interaction id
     * @param ods ODS code of MHS owner
     * @param asid ASID of end system
     * @param pk EbXml PartyId (party key) of the recipient MHS
     */     
    ArrayList<SdsTransmissionDetails> getSdsTransmissionDetails(String svcint, String ods, String asid, String pk)
    {
        if (!transmission.containsKey(svcint))        
            return null;    
        
        ArrayList<SdsTransmissionDetails> cachedDetails = transmission.get(svcint);
        ArrayList<SdsTransmissionDetails> output = new ArrayList<>();
        
        for (SdsTransmissionDetails sds : cachedDetails) {
            if (sds.getSvcIA().contentEquals(svcint)) {
                if (ods != null) {
                    if (sds.getOrgCode().contentEquals(ods)) {
                        if (asid != null) {
                            if (sds.getAsids().contains(asid))
                                output.add(sds);
                        } else {                            
                            // PK check (only if ASID is null because ASID includes and is more specific than PK)
                            if ((pk == null) || (sds.getPartyKey().contentEquals(pk)))
                                output.add(sds);
                        }
                    }                
                } else {
                    if (asid != null) {
                        if (sds.getAsids().contains(asid))
                            output.add(sds);
                    } else {                            
                        // PK check (only if ASID is null because ASID includes and is more specific than PK)
                        if ((pk == null) || (sds.getPartyKey().contentEquals(pk)))
                            output.add(sds);
                    }                    
                }
            }
        }
        
        if (!output.isEmpty())
            return output;
        return null;
    }
}
