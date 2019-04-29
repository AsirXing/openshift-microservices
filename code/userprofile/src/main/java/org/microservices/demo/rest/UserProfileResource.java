/**
 * User Profile REST Resource
 * Created by: Gbenga Taylor
 * https://github.com/gbengataylor
 * 
 * (C) 2019
 * Released under the terms of Apache-2.0 License
 */

package org.microservices.demo.rest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.List;
import java.util.Map;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.io.IOUtils;
import org.h2.util.StringUtils;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.microservices.demo.json.UserProfile;
import org.microservices.demo.json.UserProfilePhoto;
import org.microservices.demo.service.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

// using JAX-RS
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserProfileResource {

    // Using Spring-DI
    @Autowired
    // @Qualifier("${user.profile.source}")
    // TODO: figure how to make this more configurable at runtime
    // spring boot has ConditionalOnProperty that can be set on bean. can the
    // quarkus springDI processor
    // handle this...probably not at the moment.
    // for now in dev mode this can be done by modifying the hardcoded value then
    // saving the file and letting hot deploy do it's thing
    @Qualifier("jpa")
    // @Qualifier("memory")
    protected UserProfileService userProfileService;

    @GET
    public Set<UserProfile> getProfiles() {
        return userProfileService.getProfiles();
        // if had return Response.ok(profiles).build(), would require
        // @RegisterForReflection on the bean
    }

    @POST
    public Response createProfile(@Valid @NotNull UserProfile profile) {
        return userProfileService.createProfile(profile) ? Response.status(Response.Status.CREATED).build()
                : Response.status(Response.Status.BAD_REQUEST).build();
    }

    @GET
    @Path("/{id}")
    public Response getProfile(@PathParam("id") String id) {
        UserProfile profile = userProfileService.getProfile(id);
        Response.Status status = (profile != null) ? Response.Status.OK : Response.Status.NOT_FOUND;
        return Response.status(status).entity(profile).build();
    }

    @PUT
    @Path("/{id}")
    public Response updateProfile(@Valid @NotNull UserProfile profile, @PathParam("id") String id) {
        // does it exist
        return userProfileService.updateProfile(profile, id) ? Response.status(Response.Status.NO_CONTENT).build()
                : Response.status(Response.Status.BAD_REQUEST).build();
    }

// hat tip  - http://www.mastertheboss.com/jboss-frameworks/resteasy/using-rest-services-to-manage-download-and-upload-of-files
    @POST
    @Path("/{id}/photo")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    // TODO -- better IO Exception handling
    public Response uploadPhoto(MultipartFormDataInput input, @PathParam("id") String id)  throws IOException {

        Map<String, List<InputPart>> uploadForm = input.getFormDataMap();
 
        // Get file data to save   // @Produces(MediaType.APPLICATION_JSON)
   // @Consumes(MediaType.APPLICATION_JSON)
        List<InputPart> inputParts = uploadForm.get("image");
       // System.out.println("numer of parts " + inputParts.size());
        byte[] bytes = null;
        String fileName = null;

        // extract image bytes from the stream
        for (InputPart inputPart : inputParts) {
                MultivaluedMap<String, String> header = inputPart.getHeaders();
                fileName = getFileName(header, id);
                // convert the uploaded file to inputstream
                InputStream inputStream = inputPart.getBody(InputStream.class, null);
               bytes = IOUtils.toByteArray(inputStream);
        }

      //  System.out.println("read " + bytes.length + " bytes");

        // add data structure that has the id, bytes, filename..the data structure should validated that non are null, call bytes - photo
        //boolean savePhoto(id, bytes, filename); // service class .. return result based on that
        return userProfileService.saveUserProfilePhoto(new UserProfilePhoto(id, bytes, fileName)) ?
                Response.ok().build() :
                Response.status(Response.Status.BAD_REQUEST).build();
    } 

    @GET
    @Path("/{id}/photo")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
        // TODO -- better IO Exception handling
    public Response downloadPhoto(@PathParam("id") String id) throws IOException {

        UserProfilePhoto userProfilePhoto = userProfileService.getUserProfilePhoto(id);
        if(userProfilePhoto != null && !StringUtils.isNullOrEmpty(userProfilePhoto.getFileName())
                && userProfilePhoto.getImage() != null) {
            // temp local file
            String fileName = userProfilePhoto.getFileName();
          //  System.out.println("filename " + fileName);
            File file = new File(userProfilePhoto.getFileName());
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(userProfilePhoto.getImage());
            fileOutputStream.flush();
            fileOutputStream.close();
            ResponseBuilder response = Response.ok((Object) file);
            response.header("Content-Disposition", "image;filename=" + fileName);
            return response.build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();

    }

    protected String getFileName(MultivaluedMap<String, String> header, String id) {

        String[] contentDisposition = header.getFirst("Content-Disposition").split(";");

        for (String filename : contentDisposition) {

            if ((filename.trim().startsWith("filename"))) {

                String[] name = filename.split("=");

                String finalFileName = name[1].trim().replaceAll("\"", "");
                return finalFileName;
            }
        }
        return id; // use id instead
    }


    //TODO:
    /*
         Update OpenAPI yaml

        datastructure - UserProfilePhoto - id, bytes[] image, filename
        abstract class to upload and download (or just make the method not supported in in-memory impl)
            - abstract findUser method
            - code to call ImageBase64Processor to encode/decode the bytes
            - code to "save" the image..abstract method

        ImageBase64Processor
         - encode, decode to base64

         Update REadme with examples on how to upload and download images

         Save to database

         TEST native on OCP
    */
}