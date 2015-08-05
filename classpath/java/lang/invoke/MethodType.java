package java.lang.invoke;

import static avian.Assembler.*;

import avian.Classes;

import java.util.List;
import java.util.ArrayList;

public final class MethodType implements java.io.Serializable {
  final ClassLoader loader;
  final byte[] spec;
  private volatile List<Parameter> parameters;
  private volatile Result result;
  private volatile int footprint;

  MethodType(ClassLoader loader, byte[] spec) {
    this.loader = loader;
    this.spec = spec;
  }

  public String toString() {
    return Classes.makeString(spec, 0, spec.length - 1);
  }

  public int footprint() {
    parameters(); // ensure spec is parsed

    return footprint;
  }

  public Class returnType() {
    parameters(); // ensure spec is parsed

    return result.type;
  }

  public Class[] parameterArray() {
    parameters(); // ensure spec is parsed

    Class[] array = new Class[parameters.size()];
    for (int i = 0; i < parameters.size(); ++i) {
      array[i] = parameters.get(i).type;
    }

    return array;
  }
  
  public Iterable<Parameter> parameters() {
    if (parameters == null) {
      List<Parameter> list = new ArrayList();
      int i;
      int index = 0;
      int position = 0;
      for (i = 1; spec[i] != ')'; ++i) {
        switch (spec[i]) {
        case 'L': {
          int start = i;
          ++ i;
          while (spec[i] != ';') ++ i;
          
          list.add(new Parameter
                   (index,
                    position,
                    Classes.makeString(spec, start, (i - start) + 1),
                    aload));
        } break;

        case '[': {
          int start = i;
          ++ i;
          while (spec[i] == '[') ++ i;
        
          switch (spec[i]) {
          case 'L':
            ++ i;
            while (spec[i] != ';') ++ i;
            break;

          default:
            break;
          }
          
          list.add(new Parameter
                   (index,
                    position,
                    Classes.makeString(spec, start, (i - start) + 1),
                    aload));
        } break;

        case 'Z':
        case 'B':
        case 'S':
        case 'C':
        case 'I':
          list.add(new Parameter
                   (index,
                    position,
                    Classes.makeString(spec, i, 1),
                    iload));
          break;

        case 'F':
          list.add(new Parameter
                   (index,
                    position,
                    Classes.makeString(spec, i, 1),
                    fload));
          break;

        case 'J':
          list.add(new Parameter
                   (index,
                    position,
                    Classes.makeString(spec, i, 1),
                    lload));

          ++ position;
          break;

        case 'D':
          list.add(new Parameter
                   (index,
                    position,
                    Classes.makeString(spec, i, 1),
                    dload));

          ++ position;
          break;

        default: throw new AssertionError();
        }

        ++ index;
        ++ position;
      }

      footprint = position;

      ++ i;

      switch (spec[i]) {
      case 'L': {
        int start = i;
        ++ i;
        while (spec[i] != ';') ++ i;
          
        result = new Result
          (Classes.makeString(spec, start, (i - start) + 1), areturn);
      } break;

      case '[': {
        int start = i;
        ++ i;
        while (spec[i] == '[') ++ i;
        
        switch (spec[i]) {
        case 'L':
          ++ i;
          while (spec[i] != ';') ++ i;
          break;

        default:
          break;
        }
          
        result = new Result(Classes.makeString(spec, start, (i - start) + 1),
                            areturn);
      } break;

      case 'V':
        result = new Result(Classes.makeString(spec, i, 1), return_);
        break;

      case 'Z':
      case 'B':
      case 'S':
      case 'C':
      case 'I':
        result = new Result(Classes.makeString(spec, i, 1), ireturn);
        break;

      case 'F':
        result = new Result(Classes.makeString(spec, i, 1), freturn);        
        break;
        
      case 'J':
        result = new Result(Classes.makeString(spec, i, 1), lreturn);
        break;
        
      case 'D':
        result = new Result(Classes.makeString(spec, i, 1), dreturn);
        break;
        
      default: throw new AssertionError();
      }
      
      parameters = list;
    }
    
    return parameters;
  }

  public Result result() {
    parameters(); // ensure spec has been parsed

    return result;
  }

  public class Parameter {
    private final int index;
    private final int position;
    private final String spec;
    private final Class type;
    private final int load;

    private Parameter(int index,
                      int position,
                      String spec,
                      int load)
    {
      this.index = index;
      this.position = position;
      this.spec = spec;
      this.type = Classes.forCanonicalName(loader, spec);
      this.load = load;
    }

    public int index() {
      return index;
    }

    public int position() {
      return position;
    }

    public String spec() {
      return spec;
    }

    public int load() {
      return load;
    }
  }

  public class Result {
    private final String spec;
    private final Class type;
    private final int return_;

    public Result(String spec, int return_) {
      this.spec = spec;
      this.type = Classes.forCanonicalName(loader, spec);
      this.return_ = return_;
    }

    public int return_() {
      return return_; // :)
    }
  }
}
