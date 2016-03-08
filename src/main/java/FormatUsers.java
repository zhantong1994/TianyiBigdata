import org.apache.commons.io.output.StringBuilderWriter;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.partition.HashPartitioner;

import java.io.*;
import java.util.Hashtable;
import java.util.StringTokenizer;

/**
 * Created by zhantong on 16/3/7.
 */
public class FormatUsers {
    public static class Map extends Mapper<LongWritable, Text, Text, IntWritable> {
        private final IntWritable one = new IntWritable(1);
        private Text content = new Text();
        private Hashtable<String,String> formatLabel=new Hashtable<>();
        public void setup(Mapper.Context context){
            Path[] cacheFiles;

            try {
                FileSystem fs = FileSystem.get(context.getConfiguration());
                String line;
                String[] tokens;
                cacheFiles = context.getLocalCacheFiles();
                //System.out.println("file path:"+cacheFiles[0].toString());
                Path path=new Path(cacheFiles[0].toString());
                FSDataInputStream fsin = fs.open(path);
                DataInputStream in = new DataInputStream(fsin);
                BufferedReader reader=new BufferedReader(new InputStreamReader(in));
                while ((line = reader.readLine()) != null) {
                    tokens = line.split("\t", 2);
                    formatLabel.put(tokens[0], tokens[1]);
                }
                reader.close();
                in.close();
                fsin.close();
            }catch (IOException e){
                e.printStackTrace();
            }
            //System.out.println(formatLabel.toString());
        }
        public void map(LongWritable key, Text value, Context context)
                throws IOException, InterruptedException {

            String line = value.toString();
            String[] split=line.split("\t");
            String userID=split[0];
            int count=Integer.parseInt(split[3]);
            String keyWords=split[2];
            StringTokenizer tokenizer = new StringTokenizer(keyWords,",");
            while (tokenizer.hasMoreTokens()) {
                String item=tokenizer.nextToken();
                context.write(new Text(userID+"#"+formatLabel.get(item)), new IntWritable(count));
            }
        }
    }
    public static class SumCombiner extends Reducer<Text, IntWritable, Text, IntWritable> {
        private final IntWritable one = new IntWritable(1);
        private String last=" ";
        private StringBuilder out=new StringBuilder();
        private String term;
        private String item;
        public void reduce(Text key,Iterable<IntWritable> values,Context context) throws IOException,InterruptedException{
            int sum=0;
            for(IntWritable val:values){
                sum+=val.get();
            }
            context.write(key,new IntWritable(sum));
        }
    }
    public static class NewPartitioner extends HashPartitioner<Text, IntWritable> {
        public int getPartition(Text key,IntWritable value,int numReduceTasks){
            String term=key.toString().split("#")[0];
            return super.getPartition(new Text(term), value, numReduceTasks);
        }
    }
    public static class Reduce extends Reducer<Text, IntWritable, Text, Text> {
        private final IntWritable one = new IntWritable(1);
        private String last=" ";
        private StringBuilder out=new StringBuilder();
        private String term;
        private String item;
        public void reduce(Text key,Iterable<IntWritable> values,Context context) throws IOException,InterruptedException{
            String[] split=key.toString().split("#");
            term=split[0];
            item=split[1];
            if(!term.equals(last)){
                if(!last.equals(" ")){
                    out.setLength(out.length()-1);
                    context.write(new Text(last),new Text(out.toString()));
                    out=new StringBuilder();
                }
                last=term;
            }
            int sum=0;
            for (IntWritable val:values){
                sum+=val.get();
            }
            out.append(item+":"+sum+";");
        }
        public void cleanup(Context context) throws IOException,InterruptedException{
            out.setLength(out.length()-1);
            context.write(new Text(last),new Text(out.toString()));
        }
    }
}