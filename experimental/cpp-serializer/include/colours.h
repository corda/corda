//
// Created by Katelyn Baker on 2019-07-04.
//

#ifndef BLOB_INSPECTOR_COLOURS_H
#define BLOB_INSPECTOR_COLOURS_H

/* FOREGROUND */
#define RESET   "\x1B[0m"
#define RED     "\x1B[31m"
#define GREEN   "\x1B[32m"
#define YELLOW  "\x1B[33m"
#define BLUE    "\x1B[34m"
#define MAGENTA "\x1B[35m"
#define CYAN    "\x1B[36m"
#define WHITE   "\x1B[37m"


#define BOLD(x) "\x1B[1m" x RESET
#define UNDL(x) "\x1B[4m" x RESET

#endif //BLOB_INSPECTOR_COLOURS_H
