indent -l120 -lc120 cdc_test_helper.c

gcc -std=c99 -o cdc_test_helper -g -I$CUBRID/include -L$CUBRID/lib -lcubridcs -I$CUBRRID/include -L$CUBRID/lib -lcascci cdc_test_helper.c
