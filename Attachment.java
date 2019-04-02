import java.nio.ByteBuffer;
import java.util.LinkedList;

class Attachment
{
  private int remainingBytes;
  private ByteBuffer buffer;
  private Step step;
  private String[] parameters;
  private LinkedList<Sezione> sections;
  private String list;

  Attachment(int remainingBytes, ByteBuffer buffer, Step step,
             String[] parameters, LinkedList<Sezione> sections, String list)
  {
    this.remainingBytes = remainingBytes;
    this.buffer = buffer;
    this.step = step;
    this.parameters = parameters;
    this.sections = sections;
    this.list = list;
  }

  int getRemainingBytes()
  {
    return remainingBytes;
  }

  void setRemainingBytes(int remainingBytes)
  {
    this.remainingBytes = remainingBytes;
  }

  ByteBuffer getBuffer()
  {
    return buffer;
  }

  void setBuffer(ByteBuffer buffer)
  {
    this.buffer = buffer;
  }

  Step getStep()
  {
    return step;
  }

  void setStep(Step step)
  {
    this.step = step;
  }

  String[] getParameters()
  {
    return parameters;
  }

  void setParameters(String[] parameters)
  {
    this.parameters = parameters;
  }

  LinkedList<Sezione> getSections()
  {
    return sections;
  }

  void setSections(LinkedList<Sezione> sections)
  {
    this.sections = sections;
  }

  String getList()
  {
    return list;
  }

  void setList(String list)
  {
    this.list = list;
  }
}
