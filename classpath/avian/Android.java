/* Copyright (c) 2013, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package avian;

public class Android {
  public static VMField findField(VMClass vmClass, String name) {
    if (vmClass.fieldTable != null) {
      Classes.link(vmClass);

      for (int i = 0; i < vmClass.fieldTable.length; ++i) {
        if (getName(vmClass.fieldTable[i]).equals(name)) {
          return vmClass.fieldTable[i];
        }
      }
    }
    return null;
  }

  public static String getName(VMField vmField) {
    return new String(vmField.name, 0, vmField.name.length - 1);
  }
}
