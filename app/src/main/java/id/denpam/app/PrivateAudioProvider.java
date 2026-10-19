package id.denpam.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** Read-only content URI to let Android share a private, authenticated audio copy. */
public final class PrivateAudioProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private File target(Uri uri)throws FileNotFoundException {
        if(uri==null||getContext()==null)throw new FileNotFoundException("Invalid URI");
        if(!("content".equals(uri.getScheme()))||
           !(getContext().getPackageName()+".share").equals(uri.getAuthority()))throw new FileNotFoundException("Invalid authority");
        List<String> seg=uri.getPathSegments();
        if(seg.size()!=2||!"audio".equals(seg.get(0)))throw new FileNotFoundException("Invalid route");
        String name=seg.get(1);
        if(!name.matches("[0-9a-f]{32}\\.(webm|m4a|mp3|wav|ogg|aac|flac)"))throw new FileNotFoundException("Invalid filename");
        File file=new File(new File(getContext().getCacheDir(),"shared"),name);
        try {
            String canonicalRoot=new File(getContext().getCacheDir(),"shared").getCanonicalPath();
            if(!file.getCanonicalPath().startsWith(canonicalRoot+File.separator))throw new FileNotFoundException("Invalid path");
        }catch(IOException e){throw new FileNotFoundException("Invalid path");}
        if(!file.isFile())throw new FileNotFoundException("Audio not available");
        return file;
    }
    public static String extensionFor(String mime){
        switch(mime){
            case "audio/webm": return "webm";
            case "audio/mp4": case "audio/x-m4a": return "m4a";
            case "audio/mpeg": case "audio/mp3": return "mp3";
            case "audio/wav": case "audio/x-wav": return "wav";
            case "audio/ogg": return "ogg";
            case "audio/aac": return "aac";
            case "audio/flac": return "flac";
            default: return "webm";
        }
    }
    public static String mimeFor(String name){
        String n=name.toLowerCase(Locale.ROOT);
        if(n.endsWith(".webm"))return "audio/webm";
        if(n.endsWith(".m4a"))return "audio/mp4";
        if(n.endsWith(".mp3"))return "audio/mpeg";
        if(n.endsWith(".wav"))return "audio/wav";
        if(n.endsWith(".ogg"))return "audio/ogg";
        if(n.endsWith(".aac"))return "audio/aac";
        if(n.endsWith(".flac"))return "audio/flac";
        return "application/octet-stream";
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(!"r".equals(mode))throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(target(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri){
        try{return mimeFor(target(uri).getName());}catch(FileNotFoundException ex){return null;}
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder){
        try{
            File file=target(uri);
            String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
            MatrixCursor cursor=new MatrixCursor(cols);
            Object[] row=new Object[cols.length];
            for(int i=0;i<cols.length;i++){
                if(OpenableColumns.DISPLAY_NAME.equals(cols[i]))row[i]="DENPAM-Rekaman."+file.getName().substring(file.getName().lastIndexOf('.')+1);
                else if(OpenableColumns.SIZE.equals(cols[i]))row[i]=file.length();
                else row[i]=null;
            }
            cursor.addRow(row);return cursor;
        }catch(FileNotFoundException e){return null;}
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] selectionArgs){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] selectionArgs){throw new UnsupportedOperationException();}
}
