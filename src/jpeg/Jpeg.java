package jpeg;

import java.io.*;
import java.util.LinkedList;

import endian.BigEndian;

public class Jpeg
{
	public byte[] jfif;
	public byte[] exif_marker;
	public byte[] exif_data;
	public LinkedList<byte[]> remain_segment = new LinkedList<byte[]>();
	public byte[] compressed_data;
	
	public JpegExif exif;
	public Thumbnail thumbnail;
	
	private boolean not_finish_segment_reading;
	
	private final int HEADER_SIZE = 10;
	
	//Post: read jpeg file and divide data area into jfif.
	//Throw: IOException if the file contains wrong or unreadable information
	public Jpeg(File f) throws IOException
	{
		BufferedInputStream buff = new BufferedInputStream(new FileInputStream (f));
		
		//read the marker to make sure it is a jpeg
		byte[] file_marker = new byte[2];
		buff.read(file_marker);
		
		//check the file type
		if( (file_marker[0] & 0xFF) == 0xFF && (file_marker[1] & 0xFF) == 0xD8 )
		{
			byte[] segment = readSegment(buff);
			if ( (segment[0] & 0xFF) == 0xFF && (segment[1] & 0xFF) == 0xE0 )
			{
				jfif = segment;
				byte[] exif_segment = readSegment(buff); 
				process_exif(exif_segment);
			}
			else if ( (segment[0] & 0xFF) == 0xFF && (segment[1] & 0xFF) == 0xE1 )
				process_exif(segment);
			else throw new IOException("Error on reading exif segment");
		}
		else {
			buff.close();
			throw new IOException("It is not a jpeg/jpg file");
		}
		
		exif = new JpegExif(exif_data);
		thumbnail = exif.getThumbnail();
		
		//finish remianing segment reading
		not_finish_segment_reading = true;
		while (not_finish_segment_reading) {
			byte[] segment = readSegment(buff);
			remain_segment.add(segment);
		}
		
		//read compressed jpeg data
		compressed_data = new byte[buff.available()];
		buff.read(compressed_data);

		buff.close();
	}
	
	//Output: test whether all data could be write back
	public void write(File output) throws IOException
	{
		FileOutputStream buff = new FileOutputStream(output);
		buff.write(0xFF);
		buff.write(0xD8);
		if(jfif != null) buff.write(jfif);
		buff.write(exif_marker);
		buff.write(exif_data);
		for(byte[] segment : remain_segment)
			buff.write(segment);
		buff.write(compressed_data);
		buff.close();
	}
	
	//Post: exif segment is divided into marker and content.
	private void process_exif(byte[] exif_segment)
	{	
		//copy the marker part
		exif_marker = new byte[HEADER_SIZE];
		exif_data = new byte[exif_segment.length - HEADER_SIZE];
		for(int i=0; i<HEADER_SIZE; i++)
			exif_marker[i] = exif_segment[i];
		for(int i=0; i<exif_segment.length - HEADER_SIZE; i++ )
			exif_data[i] = exif_segment[i + HEADER_SIZE];
	}
	
	//Return: a byte array which contains an segment
	private byte[] readSegment (BufferedInputStream f) throws IOException
	{
		//mark the segment location
		f.mark(f.available());
		byte[] header = new byte[2];
		f.read(header);
		
		byte[] size_data = new byte[2];
		f.read(size_data);
		int size = BigEndian.getInt16(size_data[0], size_data[1]);
		
		if( (header[0] & 0xFF) == 0xFF ) {
			if( (header[1] & 0xFF) == 0xDA )
				not_finish_segment_reading = false;
		} else {
			System.out.printf("Error segment %02x %02x %n", header[0], header[1]);
			return null;
		}
		
		byte[] content = new byte[size+2]; //content will include header information
		content[0] = header[0];
		content[1] = header[1];
		content[2] = size_data[0];
		content[3] = size_data[1];
		for(int i=4; i<size+2; i++)
			content[i] = (byte)(f.read());
		
		return content;		
	}
}