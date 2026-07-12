package com.jjrodcast.textkit.editor.utils

object DocumentUtils {

    val complexJsonV1 = """
        {
          "type": "doc",
          "content": [
            {
              "type": "paragraph",
              "content": [
                {
                  "type": "text",
                  "text": "This is text formatting examples:",
                  "marks": [
                    {
                        "type": "textStyle",
                        "attrs": {
                            "fontSize": 30,
                            "color": "#87B340"
                        }
                    },
                    {
                       "type": "bold"
                    }
                  ]
                }
              ]
            },
            {
              "type": "paragraph",
              "content": [
                {
                  "type": "text",
                  "marks": [
                    {
                      "type": "bold"
                    },
                    {
                        "type": "textStyle",
                        "attrs": {
                            "fontSize": 24,
                            "color": "#E84396"
                        }
                    }
                  ],
                  "text": "This it's bold"
                },
                {
                  "type": "text",
                  "text": " then it's "
                },
                {
                  "type": "text",
                  "marks": [
                    {
                      "type": "italic"
                    }
                  ],
                  "text": "italic"
                },
                {
                  "type": "text",
                  "text": ", after "
                },
                {
                  "type": "text",
                  "marks": [
                    {
                      "type": "underline"
                    }
                  ],
                  "text": "underline"
                },
                {
                  "type": "text",
                  "text": " and finally is "
                },
                {
                  "type": "text",
                  "marks": [
                    {
                      "type": "link",
                      "attrs": {
                        "href": "http://www.autodesk.com",
                        "target": "_blank",
                        "class": null
                      }
                    }
                  ],
                  "text": "hiperlink"
                }
              ]
            },
            {
              "type": "paragraph",
              "content": [
                {
                  "type": "text",
                  "text": "List of numbered elements:"
                }
              ]
            },
            {
              "type": "orderedList",
              "attrs": {
                "start": 1
              },
              "content": [
                {
                  "type": "listItem",
                  "content": [
                    {
                      "type": "paragraph",
                      "content": [
                        {
                          "type": "text",
                          "text": "First "
                        },
                        {
                          "type": "text",
                          "marks": [
                            {
                              "type": "strike"
                            }
                          ],
                          "text": "item"
                        }
                      ]
                    }
                  ]
                },
                {
                  "type": "listItem",
                  "content": [
                    {
                      "type": "paragraph",
                      "content": [
                        {
                          "type": "text",
                          "marks": [
                            {
                              "type": "bold"
                            }
                          ],
                          "text": "Second"
                        },
                        {
                          "type": "text",
                          "text": " "
                        },
                        {
                          "type": "text",
                          "marks": [
                            {
                              "type": "italic"
                            }
                          ],
                          "text": "i"
                        },
                        {
                          "type": "text",
                          "marks": [
                            {
                              "type": "underline"
                            }
                          ],
                          "text": "tem"
                        }
                      ]
                    }
                  ]
                },
                {
                  "type": "listItem",
                  "content": [
                    {
                      "type": "paragraph",
                      "content": [
                        {
                          "type": "text",
                          "marks": [
                            {
                              "type": "bold"
                            },
                            {
                              "type": "highlight"
                            }
                          ],
                          "text": "Third"
                        },
                        {
                          "type": "text",
                          "text": " "
                        },
                        {
                          "type": "text",
                          "marks": [
                            {
                              "type": "underline"
                            }
                          ],
                          "text": "i"
                        },
                        {
                          "type": "text",
                          "marks": [
                            {
                              "type": "italic"
                            },
                            {
                              "type": "underline"
                            }
                          ],
                          "text": "t"
                        },
                        {
                          "type": "text",
                          "marks": [
                            {
                              "type": "underline"
                            },
                            {
                              "type": "highlight"
                            }
                          ],
                          "text": "e"
                        },
                        {
                          "type": "text",
                          "marks": [
                            {
                              "type": "bold"
                            },
                            {
                              "type": "underline"
                            }
                          ],
                          "text": "m"
                        }
                      ]
                    },
                    {
                      "type": "orderedList",
                      "attrs": {
                        "start": 1
                      },
                      "content": [
                        {
                          "type": "listItem",
                          "content": [
                            {
                              "type": "paragraph",
                              "content": [
                                {
                                  "type": "text",
                                  "text": "First "
                                },
                                {
                                  "type": "text",
                                  "marks": [
                                    {
                                      "type": "italic"
                                    }
                                  ],
                                  "text": "nested"
                                },
                                {
                                  "type": "text",
                                  "text": " item"
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
              ]
            },
            {
              "type": "paragraph"
            },
            {
              "type": "paragraph",
              "content": [
                {
                  "type": "text",
                  "text": "List of bulleted elements:"
                }
              ]
            },
            {
              "type": "bulletList",
              "content": [
                {
                  "type": "listItem",
                  "content": [
                    {
                      "type": "paragraph",
                      "content": [
                        {
                          "type": "text",
                          "marks": [
                            {
                              "type": "bold"
                            }
                          ],
                          "text": "First"
                        },
                        {
                          "type": "text",
                          "text": " item"
                        }
                      ]
                    }
                  ]
                },
                {
                  "type": "listItem",
                  "content": [
                    {
                      "type": "paragraph",
                      "content": [
                        {
                          "type": "text",
                          "text": "Second "
                        },
                        {
                          "type": "text",
                          "marks": [
                            {
                              "type": "underline"
                            }
                          ],
                          "text": "item"
                        }
                      ]
                    }
                  ]
                },
                {
                  "type": "listItem",
                  "content": [
                    {
                      "type": "paragraph",
                      "content": [
                        {
                          "type": "text",
                          "text": "Third item"
                        }
                      ]
                    },
                    {
                      "type": "bulletList",
                      "content": [
                        {
                          "type": "listItem",
                          "content": [
                            {
                              "type": "paragraph",
                              "content": [
                                {
                                  "type": "text",
                                  "marks": [
                                    {
                                      "type": "highlight"
                                    }
                                  ],
                                  "text": "First"
                                },
                                {
                                  "type": "text",
                                  "text": " nested "
                                },
                                {
                                  "type": "text",
                                  "marks": [
                                    {
                                      "type": "italic"
                                    },
                                    {
                                      "type": "underline"
                                    }
                                  ],
                                  "text": "it"
                                },
                                {
                                  "type": "text",
                                  "marks": [
                                    {
                                      "type": "italic"
                                    },
                                    {
                                      "type": "underline"
                                    },
                                    {
                                      "type": "strike"
                                    }
                                  ],
                                  "text": "e"
                                },
                                {
                                  "type": "text",
                                  "marks": [
                                    {
                                      "type": "italic"
                                    },
                                    {
                                      "type": "underline"
                                    }
                                  ],
                                  "text": "m"
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    val complexJsonV2 = """
        {
          "type": "doc",
          "content": [
            {
              "type": "paragraph",
              "content": [
                {
                  "type": "text",
                  "marks": [
                    {
                      "type": "textStyle",
                      "attrs": {
                        "fontSize": null,
                        "color": "#ec4a41"
                      }
                    }
                  ],
                  "text": "C "
                },
                {
                  "type": "text",
                  "marks": [
                    {
                      "type": "textStyle",
                      "attrs": {
                        "fontSize": null,
                        "color": "#FAA21B"
                      }
                    }
                  ],
                  "text": "O"
                },
                {
                  "type": "text",
                  "marks": [
                    {
                      "type": "textStyle",
                      "attrs": {
                        "fontSize": null,
                        "color": "#FAA21B"
                      }
                    }
                  ],
                  "text": " "
                },
                {
                  "type": "text",
                  "marks": [
                    {
                      "type": "textStyle",
                      "attrs": {
                        "fontSize": null,
                        "color": "#87B340"
                      }
                    }
                  ],
                  "text": "L "
                },
                {
                  "type": "text",
                  "marks": [
                    {
                      "type": "textStyle",
                      "attrs": {
                        "fontSize": null,
                        "color": "#4679B9"
                      }
                    }
                  ],
                  "text": "O"
                },
                {
                  "type": "text",
                  "marks": [
                    {
                      "type": "textStyle",
                      "attrs": {
                        "fontSize": null,
                        "color": "#4679B9"
                      }
                    }
                  ],
                  "text": " "
                },
                {
                  "type": "text",
                  "marks": [
                    {
                      "type": "textStyle",
                      "attrs": {
                        "fontSize": null,
                        "color": "#A76EF5"
                      }
                    }
                  ],
                  "text": "R"
                },
                {
                  "type": "text",
                  "marks": [
                    {
                      "type": "textStyle",
                      "attrs": {
                        "fontSize": null,
                        "color": "#A76EF5"
                      }
                    }
                  ],
                  "text": " "
                },
                {
                  "type": "text",
                  "marks": [
                    {
                      "type": "bold"
                    }
                  ],
                  "text": "bold"
                },
                {
                  "type": "text",
                  "text": " "
                },
                {
                  "type": "text",
                  "marks": [
                    {
                      "type": "italic"
                    }
                  ],
                  "text": "italic"
                },
                {
                  "type": "text",
                  "text": " "
                },
                {
                  "type": "text",
                  "marks": [
                    {
                      "type": "underline"
                    }
                  ],
                  "text": "underline"
                },
                {
                  "type": "text",
                  "text": " "
                },
                {
                  "type": "text",
                  "marks": [
                    {
                      "type": "strike"
                    }
                  ],
                  "text": "strike"
                },
                {
                  "type": "text",
                  "text": " "
                },
                {
                  "type": "text",
                  "marks": [
                    {
                      "type": "textStyle",
                      "attrs": {
                        "fontSize": 18,
                        "color": ""
                      }
                    }
                  ],
                  "text": "large"
                },
                {
                  "type": "text",
                  "text": " "
                },
                {
                  "type": "text",
                  "marks": [
                    {
                      "type": "textStyle",
                      "attrs": {
                        "fontSize": 10,
                        "color": ""
                      }
                    }
                  ],
                  "text": "small"
                }
              ]
            },
            {
              "type": "orderedList",
              "attrs": {
                "start": 1
              },
              "content": [
                {
                  "type": "listItem",
                  "content": [
                    {
                      "type": "paragraph",
                      "content": [
                        {
                          "type": "text",
                          "text": "ordered list item one"
                        }
                      ]
                    }
                  ]
                },
                {
                  "type": "listItem",
                  "content": [
                    {
                      "type": "paragraph",
                      "content": [
                        {
                          "type": "text",
                          "text": "ordered list item two"
                        }
                      ]
                    }
                  ]
                }
              ]
            },
            {
              "type": "bulletList",
              "content": [
                {
                  "type": "listItem",
                  "content": [
                    {
                      "type": "paragraph",
                      "content": [
                        {
                          "type": "text",
                          "text": "unordered list item A"
                        }
                      ]
                    }
                  ]
                },
                {
                  "type": "listItem",
                  "content": [
                    {
                      "type": "paragraph",
                      "content": [
                        {
                          "type": "text",
                          "text": "unordered list item B"
                        }
                      ]
                    }
                  ]
                }
              ]
            },
            {
              "type": "taskList",
              "content": [
                {
                  "type": "taskItem",
                  "attrs": {
                    "checked": true
                  },
                  "content": [
                    {
                      "type": "paragraph",
                      "content": [
                        {
                          "type": "text",
                          "text": "A list item"
                        }
                      ]
                    }
                  ]
                },
                {
                  "type": "taskItem",
                  "attrs": {
                    "checked": false
                  },
                  "content": [
                    {
                      "type": "paragraph",
                      "content": [
                        {
                          "type": "text",
                          "text": "And another one"
                        }
                      ]
                    }
                  ]
                }
              ]
            },
            {
              "type": "paragraph",
              "content": [
                {
                  "type": "text",
                  "marks": [
                    {
                      "type": "link",
                      "attrs": {
                        "href": "http://github.com",
                        "target": "_blank",
                        "class": null
                      }
                    }
                  ],
                  "text": "link"
                }
              ]
            },
            {
              "type": "paragraph",
              "content": [
                {
                  "type": "text",
                  "marks": [
                    {
                      "type": "highlight"
                    }
                  ],
                  "text": "highlight"
                }
              ]
            }
          ]
        }
    """.trimIndent()

    val complexJsonV3 = """
        {
           "type":"doc",
           "content":[
              {
                 "type":"orderedList",
                 "attrs":{
                    "start":1
                 },
                 "content":[
                    {
                       "type":"listItem",
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "text":"dsd"
                                }
                             ]
                          }
                       ]
                    },
                    {
                       "type":"listItem",
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "text":"dad"
                                }
                             ]
                          }
                       ]
                    },
                    {
                       "type":"listItem",
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "text":"dad"
                                }
                             ]
                          }
                       ]
                    }
                 ]
              },
              {
                 "type":"taskList",
                 "content":[
                    {
                       "type":"taskItem",
                       "attrs":{
                          "checked":false
                       },
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "text":"one"
                                }
                             ]
                          }
                       ]
                    },
                    {
                       "type":"taskItem",
                       "attrs":{
                          "checked":true
                       },
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "text":"two"
                                }
                             ]
                          }
                       ]
                    },
                    {
                       "type":"taskItem",
                       "attrs":{
                          "checked":false
                       },
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "text":"ddsd"
                                }
                             ]
                          }
                       ]
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"bold"
                          },
                          {
                             "type":"italic"
                          }
                       ],
                       "text":"This is a test."
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"text",
                       "text":"Test"
                    }
                 ]
              },
              { "type": "paragraph" },
              { "type": "paragraph" }
           ]
        }
    """.trimIndent()

    val complexJsonV4 = """
        {
          "type": "doc",
          "content": [
            {
              "type": "orderedList",
              "attrs": {
                "start": 1
              },
              "content": [
                {
                  "type": "listItem",
                  "content": [
                    {
                      "type": "paragraph",
                      "content": [
                        {
                          "type": "text",
                          "text": "one"
                        }
                      ]
                    },
                    {
                      "type": "orderedList",
                      "attrs": {
                        "start": 1
                      },
                      "content": [
                        {
                          "type": "listItem",
                          "content": [
                            {
                              "type": "paragraph",
                              "content": [
                                {
                                  "type": "text",
                                  "text": "nested one"
                                }
                              ]
                            },
                            {
                              "type": "orderedList",
                              "attrs": {
                                "start": 1
                              },
                              "content": [
                                {
                                  "type": "listItem",
                                  "content": [
                                    {
                                      "type": "paragraph",
                                      "content": [
                                        {
                                          "type": "text",
                                          "text": "nested nested one"
                                        }
                                      ]
                                    },
                                    {
                                      "type": "orderedList",
                                      "attrs": {
                                        "start": 1
                                      },
                                      "content": [
                                        {
                                          "type": "listItem",
                                          "content": [
                                            {
                                              "type": "paragraph",
                                              "content": [
                                                {
                                                  "type": "text",
                                                  "text": "Nested nested nested one"
                                                }
                                              ]
                                            }
                                          ]
                                        }
                                      ]
                                    }
                                  ]
                                }
                              ]
                            }
                          ]
                        },
                        {
                          "type": "listItem",
                          "content": [
                            {
                              "type": "paragraph",
                              "content": [
                                {
                                  "type": "text",
                                  "text": "nested two"
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                  ]
                },
                {
                  "type": "listItem",
                  "content": [
                    {
                      "type": "paragraph",
                      "content": [
                        {
                          "type": "text",
                          "text": "two"
                        }
                      ]
                    }
                  ]
                },
                {
                  "type": "listItem",
                  "content": [
                    {
                      "type": "paragraph",
                      "content": [
                        {
                          "type": "text",
                          "text": "three"
                        }
                      ]
                    }
                  ]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    val complexJsonV5 = """
            {
              "type": "doc",
              "content": [
                {
                  "type": "paragraph",
                  "content": [
                    {
                      "type": "text",
                      "text": "asdasdasd"
                    }
                  ]
                },
                {
                  "type": "paragraph",
                  "content": [
                    {
                      "type": "text",
                      "text": "asdasd"
                    }
                  ]
                },
                {
                  "type": "orderedList",
                  "attrs": {
                    "start": 1
                  },
                  "content": [
                    {
                      "type": "listItem",
                      "content": [
                        {
                          "type": "paragraph",
                          "content": [
                            {
                              "type": "text",
                              "text": "asdasd"
                            }
                          ]
                        },
                        {
                          "type": "bulletList",
                          "content": [
                            {
                              "type": "listItem",
                              "content": [
                                {
                                  "type": "paragraph",
                                  "content": [
                                    {
                                      "type": "text",
                                      "text": "asasdads"
                                    }
                                  ]
                                }
                              ]
                            },
                            {
                              "type": "listItem",
                              "content": [
                                {
                                  "type": "paragraph",
                                  "content": [
                                    {
                                      "type": "text",
                                      "text": "asdasd"
                                    }
                                  ]
                                }
                              ]
                            },
                            {
                              "type": "listItem",
                              "content": [
                                {
                                  "type": "paragraph",
                                  "content": [
                                    {
                                      "type": "text",
                                      "text": "asdasd"
                                    }
                                  ]
                                }
                              ]
                            },
                            {
                              "type": "listItem",
                              "content": [
                                {
                                  "type": "paragraph",
                                  "content": [
                                    {
                                      "type": "text",
                                      "text": "asdasda"
                                    }
                                  ]
                                }
                              ]
                            },
                            {
                              "type": "listItem",
                              "content": [
                                {
                                  "type": "paragraph",
                                  "content": [
                                    {
                                      "type": "text",
                                      "text": "aasdads"
                                    }
                                  ]
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    },
                    {
                      "type": "listItem",
                      "content": [
                        {
                          "type": "paragraph",
                          "content": [
                            {
                              "type": "text",
                              "text": "asdasd"
                            }
                          ]
                        },
                        {
                          "type": "bulletList",
                          "content": [
                            {
                              "type": "listItem",
                              "content": [
                                {
                                  "type": "paragraph",
                                  "content": [
                                    {
                                      "type": "text",
                                      "text": "asdasdasd"
                                    }
                                  ]
                                }
                              ]
                            },
                            {
                              "type": "listItem",
                              "content": [
                                {
                                  "type": "paragraph",
                                  "content": [
                                    {
                                      "type": "text",
                                      "text": "aaaaa"
                                    }
                                  ]
                                }
                              ]
                            },
                            {
                              "type": "listItem",
                              "content": [
                                {
                                  "type": "paragraph",
                                  "content": [
                                    {
                                      "type": "text",
                                      "text": "asdsa"
                                    }
                                  ]
                                }
                              ]
                            },
                            {
                              "type": "listItem",
                              "content": [
                                {
                                  "type": "paragraph",
                                  "content": [
                                    {
                                      "type": "text",
                                      "text": "asdasd"
                                    }
                                  ]
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    },
                    {
                      "type": "listItem",
                      "content": [
                        {
                          "type": "paragraph",
                          "content": [
                            {
                              "type": "text",
                              "text": "asdasd"
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
    """.trimIndent()

    val emptyDocument = """{}""".trimIndent()

    val complexJsonV6 = """
        {
           "type":"doc",
           "content":[
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"textStyle",
                             "attrs":{
                                "color":"rgb(0, 0, 0)"
                             }
                          },
                          {
                             "type":"bold"
                          }
                       ],
                       "text":"Bold:"
                    },
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"textStyle",
                             "attrs":{
                                "color":"rgb(0, 0, 0)"
                             }
                          }
                       ],
                       "text":" "
                    },
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"textStyle",
                             "attrs":{
                                "color":"rgb(0, 0, 0)"
                             }
                          },
                          {
                             "type":"bold"
                          }
                       ],
                       "text":"I love ice cream I love ice cream "
                    },
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"textStyle",
                             "attrs":{
                                "color":""
                             }
                          }
                       ],
                       "text":"I love ice cream I love ice cream I love ice cream"
                    },
                    {
                       "type":"hardBreak"
                    }
                 ]
              },
              {
                 "type":"heading",
                 "attrs":{
                    "level":4
                 },
                 "content":[
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"italic"
                          }
                       ],
                       "text":"Italic: I love ice cream I love ice cream I love ice cream I love ice cream I love ice cream I love ice cream"
                    }
                 ]
              },
              {
                 "type":"heading",
                 "attrs":{
                    "level":4
                 },
                 "content":[
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"underline"
                          }
                       ],
                       "text":"Underline: I love ice creamI love ice cream I love ice cream I love ice cream I love ice cream I love ice cream"
                    }
                 ]
              },
              {
                 "type":"heading",
                 "attrs":{
                    "level":4
                 },
                 "content":[
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"strike"
                          }
                       ],
                       "text":"Strikethrough: 30 days after shipment; whichever occurs first."
                    }
                 ]
              },
              {
                 "type":"heading",
                 "attrs":{
                    "level":4
                 },
                 "content":[
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"bold"
                          }
                       ],
                       "text":"Different font colors"
                    },
                    {
                       "type":"text",
                       "text":":"
                    },
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"bold"
                          }
                       ],
                       "text":" "
                    },
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"textStyle",
                             "attrs":{
                                "color":"rgb(255, 255, 0)"
                             }
                          }
                       ],
                       "text":"I love "
                    },
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"textStyle",
                             "attrs":{
                                "color":"rgb(255, 0, 255)"
                             }
                          },
                          {
                             "type":"bold"
                          }
                       ],
                       "text":"ice cream "
                    },
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"bold"
                          }
                       ],
                       "text":"I l"
                    },
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"textStyle",
                             "attrs":{
                                "color":"rgb(255, 153, 0)"
                             }
                          },
                          {
                             "type":"bold"
                          }
                       ],
                       "text":"ove ice creamI love ice cream "
                    },
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"bold"
                          }
                       ],
                       "text":"I love ice creamI "
                    },
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"textStyle",
                             "attrs":{
                                "color":"rgb(51, 102, 255)"
                             }
                          },
                          {
                             "type":"bold"
                          }
                       ],
                       "text":"love ice creamI"
                    },
                    {
                       "type":"hardBreak",
                       "marks":[
                          {
                             "type":"textStyle",
                             "attrs":{
                                "color":"red"
                             }
                          }
                       ]
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"bold"
                          }
                       ],
                       "text":"Link"
                    },
                    {
                       "type":"text",
                       "text":":"
                    },
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"bold"
                          }
                       ],
                       "text":" "
                    },
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"link",
                             "attrs":{
                                "href":"http://www.gmail.com/",
                                "target":"_blank",
                                "rel":"noopener noreferrer",
                                "class":"external-link"
                             }
                          },
                          {
                             "type":"bold"
                          }
                       ],
                       "text":"Check this link"
                    },
                    {
                       "type":"hardBreak",
                       "marks":[
                          {
                             "type":"bold"
                          }
                       ]
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"bold"
                          }
                       ],
                       "text":"Highlight: "
                    },
                    {
                       "type":"text",
                       "text":"Wiki doesn't have this option, so the highlight should be done from the email body on the app/web"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak",
                       "marks":[
                          {
                             "type":"bold"
                          }
                       ]
                    },
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"bold"
                          }
                       ],
                       "text":"Different font sizes:"
                    },
                    {
                       "type":"hardBreak",
                       "marks":[
                          {
                             "type":"bold"
                          }
                       ]
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"text",
                       "text":"AAAAAAAAA"
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"text",
                       "text":"BBBBBBBBBBBB"
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"text",
                       "text":"CCCCCCCCCCCCCCCCC"
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"text",
                       "text":"XXXXXXXXXXXXXXXXXX"
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"hardBreak",
                       "marks":[
                          {
                             "type":"bold"
                          }
                       ]
                    },
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"bold"
                          }
                       ],
                       "text":"Numbered list:"
                    }
                 ]
              },
              {
                 "type":"orderedList",
                 "attrs":{
                    "start":1
                 },
                 "content":[
                    {
                       "type":"listItem",
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "marks":[
                                      {
                                         "type":"textStyle",
                                         "attrs":{
                                            "color":"rgb(33, 33, 33)"
                                         }
                                      }
                                   ],
                                   "text":"In one post, he claimed that the decisions made by the High Court of Justice"
                                }
                             ]
                          }
                       ]
                    },
                    {
                       "type":"listItem",
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "marks":[
                                      {
                                         "type":"textStyle",
                                         "attrs":{
                                            "color":"rgb(33, 33, 33)"
                                         }
                                      }
                                   ],
                                   "text":"led to changes in the IDF's rules of engagement at the Gaza border, enabling"
                                }
                             ]
                          }
                       ]
                    },
                    {
                       "type":"listItem",
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "marks":[
                                      {
                                         "type":"textStyle",
                                         "attrs":{
                                            "color":"rgb(33, 33, 33)"
                                         }
                                      }
                                   ],
                                   "text":"Hamas terrorists approach the border fence."
                                }
                             ]
                          }
                       ]
                    },
                    {
                       "type":"listItem",
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "text":"ddddd eg rg erg rehg rehg erh"
                                }
                             ]
                          }
                       ]
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"bold"
                          }
                       ],
                       "text":"Ordered list inside the square:"
                    }
                 ]
              },
              {
                 "type":"bulletList",
                 "content":[
                    {
                       "type":"listItem",
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "text":"30% Down payment due upon the signing of this contract, not to exceed seven calendar days from the date the contract was signed."
                                }
                             ]
                          }
                       ]
                    },
                    {
                       "type":"listItem",
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "text":"20% Due upon approval of general arrangement drawings."
                                }
                             ]
                          }
                       ]
                    },
                    {
                       "type":"listItem",
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "text":"20% Due upon release to manufacturing."
                                }
                             ]
                          }
                       ]
                    },
                    {
                       "type":"listItem",
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "text":"10% Due upon 50% completion of manufacturing."
                                }
                             ]
                          }
                       ]
                    },
                    {
                       "type":"listItem",
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "text":"10% Due prior to shipment, goods will not ship until this payment has been received by Seller."
                                }
                             ]
                          }
                       ]
                    },
                    {
                       "type":"listItem",
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "text":"5% Due upon the first piece being cycled through the system or 30 days after shipment; whichever occurs first."
                                }
                             ]
                          }
                       ]
                    },
                    {
                       "type":"listItem",
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "text":"5% Due upon acceptance of performance tests."
                                }
                             ]
                          }
                       ]
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"hardBreak",
                       "marks":[
                          {
                             "type":"italic"
                          }
                       ]
                    },
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"bold"
                          }
                       ],
                       "text":"Ordered list:"
                    },
                    {
                       "type":"hardBreak",
                       "marks":[
                          {
                             "type":"bold"
                          }
                       ]
                    }
                 ]
              },
              {
                 "type":"bulletList",
                 "content":[
                    {
                       "type":"listItem",
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "text":"30% Down payment due upon the signing of this contract, not to exceed seven calendar days from the date the contract was signed."
                                }
                             ]
                          }
                       ]
                    },
                    {
                       "type":"listItem",
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "text":"20% Due upon approval of general arrangement drawings."
                                }
                             ]
                          }
                       ]
                    },
                    {
                       "type":"listItem",
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "text":"20% Due upon release to manufacturing."
                                }
                             ]
                          }
                       ]
                    },
                    {
                       "type":"listItem",
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "text":"10% Due upon 50% completion of manufacturing."
                                }
                             ]
                          }
                       ]
                    },
                    {
                       "type":"listItem",
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "text":"10% Due prior to shipment, goods will not ship until this payment has been received by Seller."
                                }
                             ]
                          }
                       ]
                    },
                    {
                       "type":"listItem",
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "text":"5% Due upon the first piece being cycled through the system or 30 days after shipment; whichever occurs first."
                                }
                             ]
                          }
                       ]
                    },
                    {
                       "type":"listItem",
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "text":"5% Due upon acceptance of performance tests."
                                }
                             ]
                          }
                       ]
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"hardBreak"
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"bold"
                          }
                       ],
                       "text":"Checklist (both on editing mode and locked after sending)"
                    }
                 ]
              },
              {
                 "type":"bulletList",
                 "content":[
                    {
                       "type":"listItem",
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "text":"111111"
                                }
                             ]
                          }
                       ]
                    },
                    {
                       "type":"listItem",
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "text":"22222"
                                }
                             ]
                          }
                       ]
                    },
                    {
                       "type":"listItem",
                       "content":[
                          {
                             "type":"paragraph",
                             "content":[
                                {
                                   "type":"text",
                                   "text":"33333"
                                }
                             ]
                          }
                       ]
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"bold"
                          }
                       ],
                       "text":"In-line image:"
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"hardBreak",
                       "marks":[
                          {
                             "type":"bold"
                          }
                       ]
                    },
                    {
                       "type":"hardBreak",
                       "marks":[
                          {
                             "type":"textStyle",
                             "attrs":{
                                "color":""
                             }
                          }
                       ]
                    },
                    {
                       "type":"hardBreak",
                       "marks":[
                          {
                             "type":"textStyle",
                             "attrs":{
                                "color":""
                             }
                          }
                       ]
                    },
                    {
                       "type":"hardBreak",
                       "marks":[
                          {
                             "type":"textStyle",
                             "attrs":{
                                "color":""
                             }
                          }
                       ]
                    },
                    {
                       "type":"hardBreak",
                       "marks":[
                          {
                             "type":"textStyle",
                             "attrs":{
                                "color":""
                             }
                          }
                       ]
                    },
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"bold"
                          }
                       ],
                       "text":"Table with text inside"
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"hardBreak"
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"hardBreak"
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"hardBreak"
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"hardBreak"
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"hardBreak"
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"hardBreak"
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"hardBreak"
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"hardBreak"
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"text",
                       "text":"efwfef3"
                    },
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"underline"
                          }
                       ],
                       "text":"3453"
                    },
                    {
                       "type":"text",
                       "text":"45"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"text",
                       "text":"AAAAA"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"text",
                       "text":"CCCCC"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"text",
                       "text":"EEEEEEE"
                    },
                    {
                       "type":"hardBreak"
                    }
                 ]
              },
              {
                 "type":"heading",
                 "attrs":{
                    "level":2
                 },
                 "content":[
                    {
                       "type":"text",
                       "text":"sdfgsdfg"
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"text",
                       "text":"dsdfgggg"
                    },
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"bold"
                          }
                       ],
                       "text":" dg dg dg dg d"
                    },
                    {
                       "type":"text",
                       "text":"gd fg dgg"
                    },
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"textStyle",
                             "attrs":{
                                "color":"rgb(51, 102, 255)"
                             }
                          }
                       ],
                       "text":"gg g g g g"
                    },
                    {
                       "type":"hardBreak",
                       "marks":[
                          {
                             "type":"textStyle",
                             "attrs":{
                                "color":"rgb(51, 102, 255)"
                             }
                          }
                       ]
                    },
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"textStyle",
                             "attrs":{
                                "color":"rgb(51, 102, 255)"
                             }
                          }
                       ],
                       "text":"dfdfg"
                    },
                    {
                       "type":"hardBreak",
                       "marks":[
                          {
                             "type":"textStyle",
                             "attrs":{
                                "color":"rgb(51, 102, 255)"
                             }
                          }
                       ]
                    },
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"textStyle",
                             "attrs":{
                                "color":"rgb(51, 102, 255)"
                             }
                          }
                       ],
                       "text":"SDgsdgsdG"
                    },
                    {
                       "type":"hardBreak",
                       "marks":[
                          {
                             "type":"textStyle",
                             "attrs":{
                                "color":"rgb(51, 102, 255)"
                             }
                          }
                       ]
                    },
                    {
                       "type":"text",
                       "marks":[
                          {
                             "type":"textStyle",
                             "attrs":{
                                "color":"rgb(51, 102, 255)"
                             }
                          }
                       ],
                       "text":"sdgsdgg"
                    },
                    {
                       "type":"text",
                       "text":"egrergerg"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"text",
                       "text":"BBBBB"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"text",
                       "text":"DDDD"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"text",
                       "text":"HEY"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    },
                    {
                       "type":"hardBreak"
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"hardBreak"
                    }
                 ]
              },
              {
                 "type":"blockquote",
                 "content":[
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":"On 30 Jan 2025, at 13:20, Karin Zloof (Forma QA) <reply@acc-qa.autodesk.com> wrote:"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"hardBreak"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":" "
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":"Forma - TLV - QA • TLV site - QA"
                          }
                       ]
                    },
                    {
                       "type":"paragraph"
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "marks":[
                                {
                                   "type":"bold"
                                }
                             ],
                             "text":"You can respond to this by replying to this email"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "marks":[
                                {
                                   "type":"textStyle",
                                   "attrs":{
                                      "color":"rgb(93, 130, 44)"
                                   }
                                },
                                {
                                   "type":"bold"
                                }
                             ],
                             "text":"New correspondence created and is now available in your project"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":" "
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "marks":[
                                {
                                   "type":"bold"
                                }
                             ],
                             "text":"Lambda - reply by email"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":" "
                          }
                       ]
                    },
                    {
                       "type":"paragraph"
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":"Type"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":" "
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":"General Correspondence"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":" "
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":"Status"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":" "
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "marks":[
                                {
                                   "type":"textStyle",
                                   "attrs":{
                                      "color":"rgb(107, 120, 127)"
                                   }
                                }
                             ],
                             "text":"-"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":" "
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":"Due date"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":" "
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "marks":[
                                {
                                   "type":"textStyle",
                                   "attrs":{
                                      "color":"rgb(107, 120, 127)"
                                   }
                                }
                             ],
                             "text":"-"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":" "
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":"References"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":" "
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "marks":[
                                {
                                   "type":"textStyle",
                                   "attrs":{
                                      "color":"rgb(107, 120, 127)"
                                   }
                                }
                             ],
                             "text":"-"
                          }
                       ]
                    },
                    {
                       "type":"paragraph"
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "marks":[
                                {
                                   "type":"bold"
                                }
                             ],
                             "text":"Content GCOR-1230-1"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":"From"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":" "
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":"Karin Zloof <"
                          },
                          {
                             "type":"text",
                             "marks":[
                                {
                                   "type":"link",
                                   "attrs":{
                                      "href":"mailto:karin.zloof@autodesk.com",
                                      "target":"_blank",
                                      "rel":"noopener noreferrer",
                                      "class":null
                                   }
                                }
                             ],
                             "text":"karin.zloof@autodesk.com"
                          },
                          {
                             "type":"text",
                             "text":">"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":" "
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":"To"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":" "
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":"Karin Zloof <"
                          },
                          {
                             "type":"text",
                             "marks":[
                                {
                                   "type":"link",
                                   "attrs":{
                                      "href":"mailto:karin.zloof@autodesk.com",
                                      "target":"_blank",
                                      "rel":"noopener noreferrer",
                                      "class":null
                                   }
                                }
                             ],
                             "text":"karin.zloof@autodesk.com"
                          },
                          {
                             "type":"text",
                             "text":"> ,"
                          },
                          {
                             "type":"text",
                             "marks":[
                                {
                                   "type":"link",
                                   "attrs":{
                                      "href":"mailto:karin.zloof@icloud.com",
                                      "target":"_blank",
                                      "rel":"noopener noreferrer",
                                      "class":null
                                   }
                                }
                             ],
                             "text":"karin.zloof@icloud.com"
                          },
                          {
                             "type":"text",
                             "text":" (non-member) , Jan Beheerder <"
                          },
                          {
                             "type":"text",
                             "marks":[
                                {
                                   "type":"link",
                                   "attrs":{
                                      "href":"mailto:meetings.service.test@gmail.com",
                                      "target":"_blank",
                                      "rel":"noopener noreferrer",
                                      "class":null
                                   }
                                }
                             ],
                             "text":"meetings.service.test@gmail.com"
                          },
                          {
                             "type":"text",
                             "text":">"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":" "
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":"Sent date"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":" "
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":"January 30, 2025, 01:20 PM (GMT +02:00)"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":" "
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":"Content"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":" "
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "marks":[
                                {
                                   "type":"textStyle",
                                   "attrs":{
                                      "color":"rgb(236, 74, 65)"
                                   }
                                }
                             ],
                             "text":"Lambda - reply by email"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "marks":[
                                {
                                   "type":"bold"
                                }
                             ],
                             "text":"Lambda - reply by email"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "marks":[
                                {
                                   "type":"italic"
                                }
                             ],
                             "text":"Lambda - reply by email"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "marks":[
                                {
                                   "type":"underline"
                                }
                             ],
                             "text":"Lambda - reply by email"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "marks":[
                                {
                                   "type":"strike"
                                }
                             ],
                             "text":"Lambda - reply by email"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "marks":[
                                {
                                   "type":"highlight"
                                }
                             ],
                             "text":"Lambda - reply by email"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":"Lambda - reply by email"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"hardBreak"
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":" "
                          }
                       ]
                    },
                    {
                       "type":"paragraph"
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "text":" "
                          }
                       ]
                    },
                    {
                       "type":"paragraph",
                       "content":[
                          {
                             "type":"text",
                             "marks":[
                                {
                                   "type":"link",
                                   "attrs":{
                                      "href":"https://u8170603.ct.sendgrid.net/ss/c/u001.-XxcNnC-zdE-eAKkNI-61OrSw-9bVCuwqjQZlFaoora0_n6h0DDGl0lMqoCx_ePTfYoY5UahAT_3JGaHPCVj0LyGUnfCvMFL5H6Fgl3ae1-JxQ9uT5AAoXEsN-8ilJ2G1Lrz5vEDn8KjQ17Mn5lXJA/4dl/-MibkH0MS5CUdnFNslxu0w/h0/h001.-2N4GxdJw7FwnLeXSatfpPneVvPfQdck2Y0hNeSXdks",
                                      "target":"_blank",
                                      "rel":"noopener noreferrer",
                                      "class":"text-primary text-decoration-none"
                                   }
                                }
                             ],
                             "text":"Manage your email notifications or unsubscribe"
                          },
                          {
                             "type":"text",
                             "text":" from this type of email. "
                          },
                          {
                             "type":"text",
                             "marks":[
                                {
                                   "type":"textStyle",
                                   "attrs":{
                                      "color":""
                                   }
                                }
                             ],
                             "text":"(for optimal viewing experience, open the link on the web)"
                          },
                          {
                             "type":"hardBreak"
                          },
                          {
                             "type":"text",
                             "text":"Add "
                          },
                          {
                             "type":"text",
                             "marks":[
                                {
                                   "type":"link",
                                   "attrs":{
                                      "href":"mailto:acc.no-reply@autodesk.com",
                                      "target":"_blank",
                                      "rel":"noopener noreferrer",
                                      "class":"text-primary text-decoration-none"
                                   }
                                }
                             ],
                             "text":"Forma"
                          },
                          {
                             "type":"text",
                             "text":" to your address book to ensure you receive emails."
                          },
                          {
                             "type":"hardBreak"
                          },
                          {
                             "type":"text",
                             "text":"For more information, visit "
                          },
                          {
                             "type":"text",
                             "marks":[
                                {
                                   "type":"link",
                                   "attrs":{
                                      "href":"https://u8170603.ct.sendgrid.net/ss/c/u001.xTML-Yzn7iWr4KEAdiVPZoZbmnWSA2PkJfaFxUyPL1f_YOO2e_8PX31IJ3e-18N0/4dl/-MibkH0MS5CUdnFNslxu0w/h1/h001.-URevz_FJDoNpN0AHIdmM0WAL4-AmDgKFdzmOfhF5jo",
                                      "target":"_blank",
                                      "rel":"noopener noreferrer",
                                      "class":"text-primary text-decoration-none"
                                   }
                                }
                             ],
                             "text":"Forma Help"
                          },
                          {
                             "type":"text",
                             "text":"."
                          },
                          {
                             "type":"hardBreak"
                          },
                          {
                             "type":"text",
                             "text":"Email ID: f6d47a32-bf9e-4a3c-9a2b-ff2ce64cc20f"
                          },
                          {
                             "type":"hardBreak"
                          },
                          {
                             "type":"text",
                             "text":"Request ID: 1e82d95c-372f-4a2e-8ffb-80c3e2a5bdaf"
                          },
                          {
                             "type":"hardBreak"
                          },
                          {
                             "type":"hardBreak"
                          },
                          {
                             "type":"text",
                             "text":"Copyright © 2025 Autodesk, Inc. All Rights Reserved."
                          }
                       ]
                    }
                 ]
              },
              {
                 "type":"paragraph",
                 "content":[
                    {
                       "type":"hardBreak"
                    }
                 ]
              }
           ]
        }
    """.trimIndent()
}
