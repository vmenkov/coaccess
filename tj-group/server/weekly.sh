#!/bin/bash

#at -f perm.sh now + 168 hours

0 2 * * * perm.sh 1>/dev/null 2>&1


