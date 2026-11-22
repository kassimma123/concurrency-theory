package com.concurrency;
import java.util.Set;

//name - litera akcji np 'b', written - zmianna zapisywana np 'y', read to zmienne czytane np {y, z}
public record Action(char name, char written, Set<Character> read) {

    //czy ta akcja jest zalezna od akcji other?
    public boolean dependsOn(Action other){
        boolean iWriteWhatOtherUses = 
        written == other.written || other.read.contains(written); //obie akcje zapisują tą samą zmienna lub druga akcja czyta to co ja zapisuje

        boolean otherWriteWhatIRead = read.contains(other.written); //czy druga akcja zapisuje coś co ja czytam

        return iWriteWhatOtherUses || otherWriteWhatIRead;   
    }

}
